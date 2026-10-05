//
//  QueensGameViewModel.swift
//  QueensGame
//
//  The single ObservableObject driving the board: input handling, validation,
//  the Smart Auto-X system, undo/redo history, the timer, and the Royal
//  Decrees rotation.
//

import SwiftUI
import Combine

@MainActor
final class QueensGameViewModel: ObservableObject {

    // MARK: - Published game state

    @Published private(set) var puzzle: Puzzle
    /// The board. Each `Cell` carries a player-owned `base` plus the set of
    /// Queens that have auto-marked an X on it (`autoOwners`).
    @Published private(set) var cells: [[Cell]]
    @Published private(set) var analysis: BoardAnalysis
    @Published private(set) var revealedRegions: Set<Int> = []
    @Published private(set) var moves = 0
    @Published private(set) var elapsed: TimeInterval = 0
    @Published private(set) var isSolved = false
    @Published private(set) var isDaily = false

    /// Called after each solve is recorded locally (wired to Game Center).
    var onSolve: ((GameCenterManager.Solve) -> Void)?

    @Published var showGameOver = false
    @Published var showHowToPlay = false

    /// Bumped to play the board's shake animation (bad move / blocked square).
    @Published private(set) var shakeToken = 0
    /// Bumped when a new decree is proclaimed (drives the banner pulse).
    @Published private(set) var decreePulse = 0
    /// The square that most recently became a Queen (drives the pop animation).
    @Published private(set) var lastPlaced: GridPos?
    /// The square the last hint changed (drives the glow ring).
    @Published private(set) var hintedCell: GridPos?

    // MARK: - Settings

    @Published var mode: GameMode {
        didSet {
            defaults.set(mode.rawValue, forKey: Keys.mode)
            if oldValue != mode { startNewGame(daily: false) }
        }
    }
    @Published var size: Int {
        didSet {
            defaults.set(size, forKey: Keys.size)
            if oldValue != size { startNewGame(daily: false) }
        }
    }
    /// Smart Auto-X master switch — toggleable at any time from the header.
    @Published var autoXEnabled: Bool {
        didSet {
            defaults.set(autoXEnabled, forKey: Keys.autoX)
            if oldValue != autoXEnabled { reconcileAutoX() }
        }
    }
    @Published var speedDecrees: Bool {
        didSet {
            defaults.set(speedDecrees, forKey: Keys.speed)
            if oldValue != speedDecrees { startNewGame(daily: false) }
        }
    }
    @Published var theme: AppTheme {
        didSet { defaults.set(theme.rawValue, forKey: Keys.theme) }
    }

    // MARK: - Private

    private enum Keys {
        static let mode = "queens.mode"
        static let size = "queens.size"
        static let autoX = "queens.autoX"
        static let speed = "queens.speed"
        static let theme = "queens.theme"
    }

    private let defaults: UserDefaults
    private var history: [[[Cell]]] = []
    private var historyIndex = -1
    private var lastConflictCount = 0

    private var timerCancellable: AnyCancellable?
    private var decreeCancellable: AnyCancellable?
    private var startStamp: Date?
    private var frozen: TimeInterval = 0
    private var speedIndex = 0
    private var dailyKey: String?

    // Drag-to-mark session
    private enum DragKind { case mark, erase }
    private var dragActive = false
    private var dragKind: DragKind?
    private var dragChanges = 0

    // MARK: - Init

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let storedMode = GameMode(rawValue: defaults.string(forKey: Keys.mode) ?? "") ?? .standard
        let storedSize = (defaults.object(forKey: Keys.size) as? Int).map { min(9, max(6, $0)) } ?? 8
        self.mode = storedMode
        self.size = storedSize
        self.autoXEnabled = defaults.bool(forKey: Keys.autoX)
        self.speedDecrees = defaults.bool(forKey: Keys.speed)
        self.theme = AppTheme(rawValue: defaults.string(forKey: Keys.theme) ?? "") ?? .system

        let seed = UInt64.random(in: .min ... .max)
        let firstPuzzle = PuzzleGenerator.generate(size: storedSize, rules: RuleSet(), seed: seed)
        self.puzzle = firstPuzzle
        self.cells = Self.emptyBoard(storedSize)
        self.analysis = BoardRules.analyse(cells: Self.emptyBoard(storedSize), puzzle: firstPuzzle)

        startNewGame(daily: false)
    }

    private static func emptyBoard(_ n: Int) -> [[Cell]] {
        Array(repeating: Array(repeating: Cell(), count: n), count: n)
    }

    // MARK: - Round lifecycle

    func startNewGame(daily: Bool) {
        stopTimers()
        isDaily = daily
        let n = size

        var rules = RuleSet()
        var chosenDecree: Decree?
        let seed: UInt64

        if daily {
            seed = PuzzleGenerator.dailySeed(date: Date(), size: n)
            dailyKey = PuzzleGenerator.dailyKey(date: Date(), size: n)
        } else {
            seed = UInt64.random(in: .min ... .max)
            dailyKey = nil
            if mode == .decrees {
                if speedDecrees {
                    chosenDecree = .truce
                    speedIndex = 0
                } else {
                    var pickRNG = SeededRNG(seed: seed ^ 0x9E37_79B9_7F4A_7C15)
                    chosenDecree = [Decree.fogOfWar, .royalGuard, .assassinsRange, .colorLock]
                        .randomElement(using: &pickRNG) ?? .fogOfWar
                    rules.diagonalTwo = (chosenDecree == .assassinsRange)
                }
            }
        }

        var generated = PuzzleGenerator.generate(size: n, rules: rules, seed: seed)
        generated.decree = chosenDecree
        if chosenDecree == .royalGuard {
            generated.guardPos = PuzzleGenerator.pickGuard(for: generated, seed: seed)
        }
        puzzle = generated

        cells = Self.emptyBoard(n)
        revealedRegions = (chosenDecree == .fogOfWar) ? [] : Set(0..<n)
        history = [cells]
        historyIndex = 0
        moves = 0
        elapsed = 0
        frozen = 0
        isSolved = false
        showGameOver = false
        lastPlaced = nil
        hintedCell = nil
        lastConflictCount = 0

        recompute()
        startTimer()

        if mode == .decrees, !daily {
            decreePulse += 1
            if speedDecrees { startDecreeRotation() }
        }
    }

    // MARK: - Derived helpers

    var currentDecree: Decree? { mode == .decrees ? puzzle.decree : nil }
    var placedCount: Int { analysis.queens.count }
    var canUndo: Bool { historyIndex > 0 }
    var canRedo: Bool { historyIndex < history.count - 1 }

    var dailyLabel: String {
        let key = PuzzleGenerator.dailyKey(date: Date(), size: size)
        return StatsStore.isDailyDone(key: key) ? "Daily ✓" : "Daily"
    }

    var roundTag: String {
        if isDaily { return "Daily · \(PuzzleGenerator.dailyKey(date: Date(), size: puzzle.size))" }
        if let decree = currentDecree, decree != .truce { return decree.title }
        return "Standard · \(puzzle.size)×\(puzzle.size)"
    }

    func display(at pos: GridPos) -> CellDisplay { cells[pos.row][pos.col].display }
    func base(at pos: GridPos) -> CellBase { cells[pos.row][pos.col].base }
    /// True when the X shown here is purely auto-generated (drives the faint style).
    func isAutoX(at pos: GridPos) -> Bool { cells[pos.row][pos.col].isAutoX }
    func isGuard(_ pos: GridPos) -> Bool { puzzle.guardPos == pos }
    func isRevealed(_ region: Int) -> Bool { revealedRegions.contains(region) }

    /// Which sides of `pos` sit on a realm boundary (respecting the fog).
    func walls(at pos: GridPos) -> Set<WallEdge> {
        let n = puzzle.size
        let region = puzzle.regions[pos.row][pos.col]
        let fogged = currentDecree == .fogOfWar
        if fogged, !isRevealed(region) { return [] }

        func boundary(_ r: Int, _ c: Int) -> Bool {
            if r < 0 || c < 0 || r >= n || c >= n { return true }
            let other = puzzle.regions[r][c]
            if other != region { return true }
            if fogged, !isRevealed(other) { return true }
            return false
        }

        var edges: Set<WallEdge> = []
        if boundary(pos.row - 1, pos.col) { edges.insert(.top) }
        if boundary(pos.row + 1, pos.col) { edges.insert(.bottom) }
        if boundary(pos.row, pos.col - 1) { edges.insert(.leading) }
        if boundary(pos.row, pos.col + 1) { edges.insert(.trailing) }
        return edges
    }

    // MARK: - Input: tap / long-press

    /// Cycle: empty → manual × → Queen → empty.
    /// Tapping a square that only shows an *auto* X claims it as a manual mark
    /// (so it survives the owning Queen being removed).
    func tap(_ pos: GridPos) {
        guard !isSolved else { return }
        guard !isGuard(pos) else { return bump() }

        switch cells[pos.row][pos.col].base {
        case .queen:
            cells[pos.row][pos.col].base = .empty
            stripAutoX(owner: pos)
        case .manualX:
            cells[pos.row][pos.col].base = .queen
            applyAutoX(owner: pos)
        case .empty:
            cells[pos.row][pos.col].base = .manualX
        }

        reveal(pos)
        moves += 1
        commit(placedQueenAt: cells[pos.row][pos.col].base == .queen ? pos : nil)
    }

    /// Long-press shortcut — drop (or lift) a Queen directly.
    func placeQueen(_ pos: GridPos) {
        guard !isSolved else { return }
        guard !isGuard(pos) else { return bump() }

        if cells[pos.row][pos.col].base == .queen {
            cells[pos.row][pos.col].base = .empty
            stripAutoX(owner: pos)
            reveal(pos)
            moves += 1
            commit(placedQueenAt: nil)
        } else {
            cells[pos.row][pos.col].base = .queen
            applyAutoX(owner: pos)
            reveal(pos)
            moves += 1
            commit(placedQueenAt: pos)
        }
    }

    private func commit(placedQueenAt pos: GridPos?) {
        pushHistory()
        recompute()
        if let pos {
            lastPlaced = pos
            Haptics.shared.tap(intensity: 0.8)
        } else {
            Haptics.shared.tap(intensity: 0.4)
        }
    }

    // MARK: - Input: drag-to-mark

    func handleDrag(start: GridPos?, current: GridPos?) {
        guard !isSolved, let current else { return }
        if !dragActive {
            dragActive = true
            let anchor = start ?? current
            // Erase only when the anchor is a *manual* mark; otherwise paint.
            dragKind = cells[anchor.row][anchor.col].base == .manualX ? .erase : .mark
            dragChanges = 0
            if applyDragMark(anchor) { dragChanges += 1 }
        }
        if applyDragMark(current) { dragChanges += 1 }
    }

    func endDrag() {
        defer { dragActive = false; dragKind = nil; dragChanges = 0 }
        guard dragActive, dragChanges > 0 else { return }
        moves += dragChanges
        pushHistory()
        recompute()
        Haptics.shared.tap(intensity: 0.3)
    }

    private func applyDragMark(_ pos: GridPos) -> Bool {
        guard let kind = dragKind, !isGuard(pos) else { return false }
        switch (kind, cells[pos.row][pos.col].base) {
        case (.mark, .empty):
            cells[pos.row][pos.col].base = .manualX; reveal(pos); return true
        case (.erase, .manualX):
            cells[pos.row][pos.col].base = .empty; reveal(pos); return true
        default:
            return false
        }
    }

    // MARK: - Hints

    /// Spends one step of the known solution: a misplaced Queen is lifted
    /// first, otherwise the next missing Queen is dropped on its square.
    /// Returns false when there is nothing to fix (the caller keeps the hint).
    @discardableResult
    func applyHint() -> Bool {
        guard !isSolved else { return false }
        let n = puzzle.size

        for r in 0..<n {
            for c in 0..<n where cells[r][c].base == .queen && puzzle.solution[r] != c {
                let pos = GridPos(row: r, col: c)
                cells[r][c].base = .empty
                stripAutoX(owner: pos)
                moves += 1
                commit(placedQueenAt: nil)
                hintedCell = pos
                return true
            }
        }

        for r in 0..<n where cells[r][puzzle.solution[r]].base != .queen {
            let pos = GridPos(row: r, col: puzzle.solution[r])
            cells[r][pos.col].base = .queen
            applyAutoX(owner: pos)
            reveal(pos)
            moves += 1
            commit(placedQueenAt: pos)
            hintedCell = pos
            return true
        }
        return false
    }

    // MARK: - Smart Auto-X

    /// Whether an auto-fill should run at all right now.
    private var autoFillActive: Bool { autoXEnabled || puzzle.decree == .colorLock }

    /// Push `owner`'s id onto every *empty* target square's owner set.
    /// Auto-X fills the 8 neighbours + rank + file + realm; when the master
    /// switch is off but the Color Lock decree is in force, only the realm.
    private func applyAutoX(owner: GridPos) {
        guard autoFillActive else { return }
        let targets = autoXEnabled
            ? BoardRules.autoXTargets(for: owner, puzzle: puzzle)
            : BoardRules.realmTargets(for: owner, puzzle: puzzle)
        for target in targets where cells[target.row][target.col].base == .empty {
            cells[target.row][target.col].autoOwners.insert(owner)
        }
    }

    /// Strip `owner`'s id from every square. Squares whose owner set empties
    /// (and that were never manually marked) fall back to empty automatically.
    private func stripAutoX(owner: GridPos) {
        for r in 0..<puzzle.size {
            for c in 0..<puzzle.size where cells[r][c].autoOwners.contains(owner) {
                cells[r][c].autoOwners.remove(owner)
            }
        }
    }

    /// Rebuild every owner set from scratch off the current Queens + settings.
    /// Used after a toggle, a scope change, undo/redo, or Clear.
    private func recomputeAutoX() {
        for r in 0..<puzzle.size {
            for c in 0..<puzzle.size {
                cells[r][c].autoOwners.removeAll()
            }
        }
        guard autoFillActive else { return }
        for r in 0..<puzzle.size {
            for c in 0..<puzzle.size where cells[r][c].base == .queen {
                applyAutoX(owner: GridPos(row: r, col: c))
            }
        }
    }

    private func reconcileAutoX() {
        recomputeAutoX()
        recompute()
    }

    // MARK: - History

    func undo() {
        guard canUndo else { return }
        historyIndex -= 1
        cells = history[historyIndex]
        afterHistoryJump()
    }

    func redo() {
        guard canRedo else { return }
        historyIndex += 1
        cells = history[historyIndex]
        afterHistoryJump()
    }

    func clearBoard() {
        let hasContent = cells.contains { row in
            row.contains { $0.base != .empty || !$0.autoOwners.isEmpty }
        }
        guard hasContent else { return }
        cells = Self.emptyBoard(puzzle.size)
        pushHistory()
        recompute()
        Haptics.shared.tap(intensity: 0.5)
    }

    private func pushHistory() {
        hintedCell = nil
        if historyIndex < history.count - 1 {
            history.removeSubrange((historyIndex + 1)...)
        }
        history.append(cells)
        historyIndex = history.count - 1
    }

    private func afterHistoryJump() {
        hintedCell = nil
        if isSolved {
            isSolved = false
            showGameOver = false
            resumeTimer()
        }
        // Owner sets are re-derived so they always match the live settings.
        recomputeAutoX()
        if currentDecree == .fogOfWar {
            for r in 0..<puzzle.size {
                for c in 0..<puzzle.size where cells[r][c].base != .empty {
                    revealedRegions.insert(puzzle.regions[r][c])
                }
            }
        }
        recompute()
        Haptics.shared.tap(intensity: 0.3)
    }

    // MARK: - Validation + win

    private func recompute() {
        analysis = BoardRules.analyse(cells: cells, puzzle: puzzle)

        if analysis.conflicts.count > lastConflictCount {
            shakeToken += 1
            Haptics.shared.notify(.warning)
        }
        lastConflictCount = analysis.conflicts.count

        if analysis.solved, !isSolved { handleSolve() }
    }

    private func handleSolve() {
        isSolved = true
        pauseTimer()
        stopTimers()
        Haptics.shared.notify(.success)

        let seconds = Int(elapsed)
        let firstDaily = isDaily && dailyKey.map { !StatsStore.isDailyDone(key: $0) } == true
        StatsStore.recordSolve(size: puzzle.size, seconds: seconds, moves: moves)
        if isDaily, let key = dailyKey { StatsStore.recordDaily(key: key) }
        if firstDaily { StatsStore.recordDailyBest(size: puzzle.size, seconds: seconds) }
        if currentDecree != nil { StatsStore.recordDecreeWin() }
        onSolve?(GameCenterManager.Solve(size: puzzle.size, seconds: seconds, isFirstDailyCompletion: firstDaily))

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            withAnimation(.spring(response: 0.5, dampingFraction: 0.82)) {
                self?.showGameOver = true
            }
        }
    }

    private func bump() {
        shakeToken += 1
        Haptics.shared.notify(.warning)
    }

    private func reveal(_ pos: GridPos) {
        guard currentDecree == .fogOfWar else { return }
        revealedRegions.insert(puzzle.regions[pos.row][pos.col])
    }

    // MARK: - Timer

    private func startTimer() {
        startStamp = Date()
        timerCancellable = Timer.publish(every: 0.25, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in self?.tick() }
    }

    private func tick() {
        guard let start = startStamp else { return }
        elapsed = frozen + Date().timeIntervalSince(start)
    }

    func pauseTimer() {
        guard let start = startStamp else { return }
        frozen += Date().timeIntervalSince(start)
        startStamp = nil
    }

    func resumeTimer() {
        guard startStamp == nil, !isSolved else { return }
        startStamp = Date()
    }

    private func stopTimers() {
        timerCancellable?.cancel(); timerCancellable = nil
        decreeCancellable?.cancel(); decreeCancellable = nil
        startStamp = nil
    }

    // MARK: - Speed decree rotation

    private func startDecreeRotation() {
        decreeCancellable = Timer.publish(every: 30, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in self?.rotateDecree() }
    }

    private func rotateDecree() {
        guard !isSolved, mode == .decrees, speedDecrees else { return }
        let sequence: [Decree] = [.fogOfWar, .royalGuard, .colorLock, .truce]
        speedIndex = (speedIndex + 1) % sequence.count
        let next = sequence[speedIndex]

        var updated = puzzle
        if updated.decree == .royalGuard, next != .royalGuard { updated.guardPos = nil }
        updated.decree = next

        if next == .fogOfWar {
            var revealed = Set<Int>()
            for r in 0..<updated.size {
                for c in 0..<updated.size where cells[r][c].base != .empty {
                    revealed.insert(updated.regions[r][c])
                }
            }
            revealedRegions = revealed
        } else {
            revealedRegions = Set(0..<updated.size)
        }

        if next == .royalGuard, updated.guardPos == nil {
            updated.guardPos = liveGuard(for: updated)
        }

        withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
            puzzle = updated
        }
        decreePulse += 1
        Haptics.shared.notify(.success)
        recompute()
    }

    private func liveGuard(for puzzle: Puzzle) -> GridPos? {
        for _ in 0..<600 {
            let r = Int.random(in: 0..<puzzle.size)
            let c = Int.random(in: 0..<puzzle.size)
            if puzzle.solution[r] != c, cells[r][c].base == .empty { return GridPos(row: r, col: c) }
        }
        return nil
    }
}

#if DEBUG
extension QueensGameViewModel {
    /// Places the whole known solution — used by UI tests and previews.
    func autoSolveForTesting() {
        cells = Self.emptyBoard(puzzle.size)
        for r in 0..<puzzle.size {
            cells[r][puzzle.solution[r]].base = .queen
        }
        recomputeAutoX()
        pushHistory()
        recompute()
    }
}
#endif
