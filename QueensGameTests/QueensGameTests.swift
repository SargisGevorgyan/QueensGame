//
//  QueensGameTests.swift
//  QueensGameTests
//

import XCTest
@testable import QueensGame

final class QueensGameTests: XCTestCase {

    // MARK: - Helpers

    private func board(_ n: Int) -> [[Cell]] {
        Array(repeating: Array(repeating: Cell(), count: n), count: n)
    }

    private func scratchDefaults() -> UserDefaults {
        let suite = "queens.tests.\(UUID().uuidString)"
        let d = UserDefaults(suiteName: suite)!
        d.removePersistentDomain(forName: suite)
        return d
    }

    /// Every realm is a single 4-connected blob.
    private func regionsAreContiguous(_ puzzle: Puzzle) -> Bool {
        let n = puzzle.size
        var counts = Array(repeating: 0, count: n)
        var firstCell = Array(repeating: GridPos?.none, count: n)
        for r in 0..<n {
            for c in 0..<n {
                let k = puzzle.regions[r][c]
                counts[k] += 1
                if firstCell[k] == nil { firstCell[k] = GridPos(row: r, col: c) }
            }
        }
        for k in 0..<n {
            guard let start = firstCell[k] else { return false }
            var seen: Set<GridPos> = [start]
            var stack = [start]
            while let p = stack.popLast() {
                for (dr, dc) in [(1, 0), (-1, 0), (0, 1), (0, -1)] {
                    let nr = p.row + dr, nc = p.col + dc
                    guard nr >= 0, nc >= 0, nr < n, nc < n else { continue }
                    let np = GridPos(row: nr, col: nc)
                    if puzzle.regions[nr][nc] == k, !seen.contains(np) {
                        seen.insert(np); stack.append(np)
                    }
                }
            }
            if seen.count != counts[k] { return false }
        }
        return true
    }

    private func solutionObeysRules(_ puzzle: Puzzle) -> Bool {
        let n = puzzle.size
        let cols = puzzle.solution
        guard Set(cols).count == n else { return false }
        for r in 0..<(n - 1) where abs(cols[r] - cols[r + 1]) <= 1 { return false }
        var realms = Set<Int>()
        for r in 0..<n { realms.insert(puzzle.regions[r][cols[r]]) }
        return realms.count == n
    }

    // MARK: - Generator

    func testGeneratedPuzzlesAreUniqueAndWellFormed() {
        for size in 6...9 {
            for seed in UInt64(1)...UInt64(3) {
                let puzzle = PuzzleGenerator.generate(size: size, rules: RuleSet(), seed: seed &* 2_654_435_761)
                XCTAssertEqual(puzzle.size, size)
                XCTAssertTrue(solutionObeysRules(puzzle), "solution invalid for size \(size) seed \(seed)")
                XCTAssertTrue(regionsAreContiguous(puzzle), "regions not contiguous for size \(size) seed \(seed)")
                let solutions = PuzzleGenerator.countSolutions(size: size,
                                                              regions: puzzle.regions,
                                                              rules: puzzle.ruleSet,
                                                              cap: 3)
                XCTAssertEqual(solutions, 1, "puzzle not unique for size \(size) seed \(seed)")
            }
        }
    }

    func testDailyPuzzleIsDeterministic() {
        let date = Date(timeIntervalSince1970: 1_760_000_000)
        let a = PuzzleGenerator.generate(size: 8, rules: RuleSet(),
                                         seed: PuzzleGenerator.dailySeed(date: date, size: 8))
        let b = PuzzleGenerator.generate(size: 8, rules: RuleSet(),
                                         seed: PuzzleGenerator.dailySeed(date: date, size: 8))
        XCTAssertEqual(a.regions, b.regions)
        XCTAssertEqual(a.solution, b.solution)
        XCTAssertEqual(a.colorMap, b.colorMap)
    }

    func testAssassinsRangeSolutionAvoidsDiagonalTwo() {
        for seed in UInt64(1)...UInt64(3) {
            let puzzle = PuzzleGenerator.generate(size: 8, rules: RuleSet(diagonalTwo: true),
                                                  seed: seed &* 1_000_003)
            let cols = puzzle.solution
            for r in 0..<(8 - 2) {
                XCTAssertNotEqual(abs(cols[r] - cols[r + 2]), 2,
                                  "diagonal-two clash slipped into an Assassin's Range solution")
            }
            XCTAssertEqual(PuzzleGenerator.countSolutions(size: 8, regions: puzzle.regions,
                                                          rules: RuleSet(diagonalTwo: true), cap: 3), 1)
        }
    }

    // MARK: - Validation

    func testDuplicateInRowIsFlagged() {
        let puzzle = PuzzleGenerator.generate(size: 6, rules: RuleSet(), seed: 99)
        var cells = board(6)
        cells[0][0].base = .queen
        cells[0][4].base = .queen
        let a = BoardRules.analyse(cells: cells, puzzle: puzzle)
        XCTAssertTrue(a.conflicts.contains(GridPos(row: 0, col: 0)))
        XCTAssertTrue(a.conflicts.contains(GridPos(row: 0, col: 4)))
        XCTAssertFalse(a.solved)
    }

    func testTouchingQueensAreFlagged() {
        let puzzle = PuzzleGenerator.generate(size: 6, rules: RuleSet(), seed: 7)
        var cells = board(6)
        cells[1][1].base = .queen
        cells[2][2].base = .queen
        let a = BoardRules.analyse(cells: cells, puzzle: puzzle)
        XCTAssertEqual(a.conflicts.count, 2)
    }

    func testAssassinsRangeFlagsDiagonalTwo() {
        let puzzle = PuzzleGenerator.generate(size: 7, rules: RuleSet(diagonalTwo: true), seed: 11)
        var cells = board(7)
        cells[1][1].base = .queen
        cells[3][3].base = .queen
        let a = BoardRules.analyse(cells: cells, puzzle: puzzle)
        XCTAssertTrue(a.conflicts.contains(GridPos(row: 1, col: 1)))

        let standard = Puzzle(size: 7, regions: puzzle.regions, solution: puzzle.solution,
                              ruleSet: RuleSet(diagonalTwo: false), colorMap: puzzle.colorMap,
                              decree: nil, guardPos: nil)
        XCTAssertTrue(BoardRules.analyse(cells: cells, puzzle: standard).conflicts.isEmpty)
    }

    func testPlacingFullSolutionSolvesTheBoard() {
        let puzzle = PuzzleGenerator.generate(size: 8, rules: RuleSet(), seed: 2024)
        var cells = board(8)
        for r in 0..<8 { cells[r][puzzle.solution[r]].base = .queen }
        let a = BoardRules.analyse(cells: cells, puzzle: puzzle)
        XCTAssertTrue(a.conflicts.isEmpty)
        XCTAssertTrue(a.solved)
        XCTAssertTrue(a.rowDone.allSatisfy { $0 })
        XCTAssertTrue(a.colDone.allSatisfy { $0 })
        XCTAssertTrue(a.regionDone.allSatisfy { $0 })
    }

    // MARK: - Auto-X target geometry

    func testAutoXTargetsCoverNeighboursRankFileAndRealm() {
        let puzzle = PuzzleGenerator.generate(size: 8, rules: RuleSet(), seed: 3)
        let queen = GridPos(row: 3, col: 3)
        let targets = Set(BoardRules.autoXTargets(for: queen, puzzle: puzzle))

        XCTAssertTrue(targets.contains(GridPos(row: 2, col: 2)))    // diagonal neighbour
        XCTAssertTrue(targets.contains(GridPos(row: 4, col: 4)))    // diagonal neighbour
        XCTAssertTrue(targets.contains(GridPos(row: 3, col: 7)))    // same rank
        XCTAssertTrue(targets.contains(GridPos(row: 7, col: 3)))    // same file

        let region = puzzle.regions[queen.row][queen.col]
        for r in 0..<8 {
            for c in 0..<8 where puzzle.regions[r][c] == region && !(r == 3 && c == 3) {
                XCTAssertTrue(targets.contains(GridPos(row: r, col: c)))
            }
        }

        // A square that is neither adjacent, nor in the rank/file, nor in the
        // realm must not be a target.
        for r in 0..<8 {
            for c in 0..<8 {
                let neighbour = abs(r - 3) <= 1 && abs(c - 3) <= 1
                let line = r == 3 || c == 3
                let realm = puzzle.regions[r][c] == region
                if !neighbour && !line && !realm {
                    XCTAssertFalse(targets.contains(GridPos(row: r, col: c)))
                }
            }
        }
    }

    // MARK: - Smart Auto-X (view model)

    @MainActor
    func testAutoXFillsNeighboursAndPreservesManualMarks() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        vm.autoXEnabled = true

        // A deliberate manual mark next to where the Queen will go.
        vm.tap(GridPos(row: 1, col: 1))
        XCTAssertEqual(vm.base(at: GridPos(row: 1, col: 1)), .manualX)

        vm.placeQueen(GridPos(row: 2, col: 2))
        XCTAssertEqual(vm.display(at: GridPos(row: 2, col: 3)), .x)      // auto-filled
        XCTAssertTrue(vm.isAutoX(at: GridPos(row: 2, col: 3)))
        XCTAssertEqual(vm.base(at: GridPos(row: 1, col: 1)), .manualX)   // manual untouched
        XCTAssertFalse(vm.isAutoX(at: GridPos(row: 1, col: 1)))

        // Remove the Queen — only its own auto marks disappear.
        vm.placeQueen(GridPos(row: 2, col: 2))
        XCTAssertEqual(vm.display(at: GridPos(row: 2, col: 3)), .empty)
        XCTAssertEqual(vm.base(at: GridPos(row: 1, col: 1)), .manualX)
    }

    @MainActor
    func testTappingAnAutoXPromotesItToManual() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        vm.autoXEnabled = true
        vm.placeQueen(GridPos(row: 2, col: 2))

        let neighbour = GridPos(row: 3, col: 3)
        XCTAssertTrue(vm.isAutoX(at: neighbour))
        vm.tap(neighbour)                                   // claim it
        XCTAssertEqual(vm.base(at: neighbour), .manualX)

        vm.placeQueen(GridPos(row: 2, col: 2))             // remove the Queen
        XCTAssertEqual(vm.display(at: neighbour), .x)       // the mark persists
        XCTAssertEqual(vm.base(at: neighbour), .manualX)
    }

    @MainActor
    func testAutoXOwnershipIsSharedBetweenQueens() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        vm.autoXEnabled = true
        let shared = GridPos(row: 3, col: 3)               // touches (2,2) and (4,4)

        vm.placeQueen(GridPos(row: 2, col: 2))
        XCTAssertTrue(vm.isAutoX(at: shared))
        vm.placeQueen(GridPos(row: 4, col: 4))
        XCTAssertTrue(vm.isAutoX(at: shared))

        vm.placeQueen(GridPos(row: 2, col: 2))             // remove one owner
        XCTAssertEqual(vm.display(at: shared), .x, "still owned by (4,4)")
        vm.placeQueen(GridPos(row: 4, col: 4))             // remove the other
        XCTAssertEqual(vm.display(at: shared), .empty)
    }

    @MainActor
    func testTogglingAutoXOffClearsAutoOnlyMarksAndBackOnReapplies() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        vm.autoXEnabled = true
        vm.tap(GridPos(row: 0, col: 0))                    // a manual X
        vm.placeQueen(GridPos(row: 2, col: 2))
        let neighbour = GridPos(row: 2, col: 3)
        XCTAssertTrue(vm.isAutoX(at: neighbour))

        vm.autoXEnabled = false
        XCTAssertEqual(vm.display(at: neighbour), .empty)          // auto swept
        XCTAssertEqual(vm.base(at: GridPos(row: 0, col: 0)), .manualX)  // manual kept

        vm.autoXEnabled = true
        XCTAssertEqual(vm.display(at: neighbour), .x)              // re-applied
    }

    @MainActor
    func testAutoXFillsRankFileAndRealm() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        vm.autoXEnabled = true
        let queen = GridPos(row: 2, col: 2)
        vm.placeQueen(queen)
        XCTAssertEqual(vm.display(at: GridPos(row: 2, col: 6)), .x)   // same rank
        XCTAssertEqual(vm.display(at: GridPos(row: 6, col: 2)), .x)   // same file

        // Every empty realm-mate of the Queen is auto-marked.
        let region = vm.puzzle.regions[queen.row][queen.col]
        for r in 0..<vm.puzzle.size {
            for c in 0..<vm.puzzle.size where vm.puzzle.regions[r][c] == region && !(r == 2 && c == 2) {
                XCTAssertEqual(vm.display(at: GridPos(row: r, col: c)), .x)
            }
        }
    }

    // MARK: - Cycle / history

    @MainActor
    func testTapCyclesAndUndoRedoRestore() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        vm.autoXEnabled = false
        let p = GridPos(row: 0, col: 0)

        vm.tap(p); XCTAssertEqual(vm.display(at: p), .x)
        vm.tap(p); XCTAssertEqual(vm.display(at: p), .queen)
        XCTAssertEqual(vm.moves, 2)

        vm.undo(); XCTAssertEqual(vm.display(at: p), .x)
        vm.undo(); XCTAssertEqual(vm.display(at: p), .empty)
        vm.redo(); XCTAssertEqual(vm.display(at: p), .x)
    }

    @MainActor
    func testClearBoardRemovesEveryMark() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        vm.autoXEnabled = true
        vm.placeQueen(GridPos(row: 2, col: 2))
        vm.tap(GridPos(row: 0, col: 0))
        vm.clearBoard()
        XCTAssertEqual(vm.placedCount, 0)
        for row in 0..<vm.puzzle.size {
            for col in 0..<vm.puzzle.size {
                XCTAssertEqual(vm.display(at: GridPos(row: row, col: col)), .empty)
            }
        }
    }

    @MainActor
    func testAutoSolveTriggersWin() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        vm.autoSolveForTesting()
        XCTAssertTrue(vm.isSolved)
        XCTAssertTrue(vm.analysis.solved)
    }

    // MARK: - Monetization

    func testOnlyDecreesRequiresPremium() {
        XCTAssertFalse(GameMode.standard.requiresPremium)
        XCTAssertTrue(GameMode.decrees.requiresPremium)
    }

    @MainActor
    func testFreeStoreGatesDecreesButNotStandard() {
        let defaults = scratchDefaults()
        let store = PremiumStore(hints: HintWallet(defaults: defaults), defaults: defaults)
        XCTAssertFalse(store.isPremium)
        XCTAssertTrue(store.canPlay(.standard))
        XCTAssertFalse(store.canPlay(.decrees))
    }

    // MARK: - Hints

    @MainActor
    func testHintPlacesACorrectQueen() throws {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        XCTAssertTrue(vm.applyHint())
        let pos = try XCTUnwrap(vm.hintedCell)
        XCTAssertEqual(vm.base(at: pos), .queen)
        XCTAssertEqual(vm.puzzle.solution[pos.row], pos.col)
        XCTAssertEqual(vm.placedCount, 1)
    }

    @MainActor
    func testHintLiftsAMisplacedQueenFirst() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        let wrong = GridPos(row: 0, col: (vm.puzzle.solution[0] + 1) % vm.puzzle.size)
        vm.placeQueen(wrong)
        XCTAssertTrue(vm.applyHint())
        XCTAssertEqual(vm.hintedCell, wrong)
        XCTAssertEqual(vm.base(at: wrong), .empty)
        XCTAssertEqual(vm.placedCount, 0)
    }

    @MainActor
    func testHintsAloneSolveThePuzzleThenStop() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        for _ in 0..<vm.puzzle.size { XCTAssertTrue(vm.applyHint()) }
        XCTAssertTrue(vm.isSolved)
        XCTAssertFalse(vm.applyHint())
    }

    @MainActor
    func testHintWalletStartsWithFreeHintsAndRefillsDaily() {
        let defaults = scratchDefaults()
        let day0 = Date(timeIntervalSince1970: 1_800_000_000)
        let day1 = Calendar.current.date(byAdding: .day, value: 1, to: day0)!

        let wallet = HintWallet(defaults: defaults, now: day0)
        XCTAssertEqual(wallet.balance, HintWallet.freeCap)
        for _ in 0..<HintWallet.freeCap { XCTAssertTrue(wallet.spend()) }
        XCTAssertFalse(wallet.spend())
        XCTAssertEqual(wallet.balance, 0)

        wallet.refillIfNewDay(now: day0)               // same day: nothing
        XCTAssertEqual(wallet.balance, 0)

        let reopened = HintWallet(defaults: defaults, now: day1)
        XCTAssertEqual(reopened.balance, 1)            // +1 on a new day
        reopened.credit(PremiumStore.hintPackSize)
        XCTAssertEqual(HintWallet(defaults: defaults, now: day1).balance, 1 + PremiumStore.hintPackSize)
    }

    @MainActor
    func testDailyRefillNeverExceedsFreeCap() {
        let defaults = scratchDefaults()
        let day0 = Date(timeIntervalSince1970: 1_800_000_000)
        _ = HintWallet(defaults: defaults, now: day0)
        let later = Calendar.current.date(byAdding: .day, value: 5, to: day0)!
        XCTAssertEqual(HintWallet(defaults: defaults, now: later).balance, HintWallet.freeCap)
    }

    // MARK: - Game Center

    func testDailyStreakCountsConsecutiveDaysEndingToday() {
        let cal = Calendar.current
        let today = Date()
        func key(_ daysAgo: Int, _ size: Int = 8) -> String {
            PuzzleGenerator.dailyKey(date: cal.date(byAdding: .day, value: -daysAgo, to: today)!, size: size)
        }
        XCTAssertEqual(StatsStore.dailyStreak(doneKeys: [], asOf: today), 0)
        XCTAssertEqual(StatsStore.dailyStreak(doneKeys: [key(1)], asOf: today), 0)    // not today
        XCTAssertEqual(StatsStore.dailyStreak(doneKeys: [key(0), key(1, 6), key(2, 9)], asOf: today), 3)
        XCTAssertEqual(StatsStore.dailyStreak(doneKeys: [key(0), key(0, 6), key(2)], asOf: today), 1)  // gap
    }

    func testAchievementProgress() {
        XCTAssertTrue(GameCenterCatalog.progress(for: .init()).isEmpty)

        let p = GameCenterCatalog.progress(for: .init(totalSolved: 5, dailyStreak: 3, everSolvedDaily: true,
                                                      decreeWins: 1, bestNineSeconds: 179))
        XCTAssertEqual(p[.firstSolve], 100)
        XCTAssertEqual(p[.solves25], 20)
        XCTAssertEqual(p[.solves100], 5)
        XCTAssertEqual(p[.firstDaily], 100)
        XCTAssertEqual(p[.dailyStreak3], 100)
        XCTAssertEqual(p[.dailyStreak7]!, 300.0 / 7, accuracy: 0.001)
        XCTAssertEqual(p[.firstDecree], 100)
        XCTAssertEqual(p[.decrees10], 10)
        XCTAssertEqual(p[.swiftNine], 100)

        let slow = GameCenterCatalog.progress(for: .init(totalSolved: 500, bestNineSeconds: 180))
        XCTAssertEqual(slow[.solves100], 100)
        XCTAssertNil(slow[.swiftNine])
    }

    func testGameCenterIdsAreUnique() {
        let ids = GameCenterCatalog.allLeaderboardIDs + GameCenterCatalog.Achievement.allCases.map(\.id)
        XCTAssertEqual(Set(ids).count, ids.count)
        XCTAssertTrue(ids.allSatisfy { $0.count <= 100 })
    }

    @MainActor
    func testSolveIsReportedOnceAndRandomRoundsSkipLeaderboard() {
        let vm = QueensGameViewModel(defaults: scratchDefaults())
        var reports: [GameCenterManager.Solve] = []
        vm.onSolve = { reports.append($0) }
        vm.autoSolveForTesting()
        XCTAssertEqual(reports.count, 1)
        XCTAssertEqual(reports.first?.size, vm.puzzle.size)
        XCTAssertEqual(reports.first?.isFirstDailyCompletion, false)
    }
}
