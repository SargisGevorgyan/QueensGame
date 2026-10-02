//
//  GameEngine.kt
//  QueensGame (Android)
//
//  The platform-free heart of the game: input handling, validation, the
//  Smart Auto-X system, undo/redo history, the timer and the Royal Decrees
//  rotation. A port of the iOS `QueensGameViewModel`, minus SwiftUI.
//  The Android `GameViewModel` drives it and publishes [snapshot]s to Compose.
//

package com.app.queensgame.core

import java.time.LocalDate
import kotlin.random.Random

/** Side effects the UI turns into haptics and the victory overlay. */
sealed interface GameEvent {
    /** A light tap; [intensity] is 0..1. */
    data class Tap(val intensity: Float) : GameEvent
    data object Warning : GameEvent
    data object Success : GameEvent
    data object Solved : GameEvent
}

/** Everything the UI renders, as one immutable value. */
data class GameSnapshot(
    val puzzle: Puzzle,
    val cells: Board,
    val analysis: BoardAnalysis,
    val revealedRegions: Set<Int>,
    val moves: Int,
    val elapsedSeconds: Int,
    val isSolved: Boolean,
    val isDaily: Boolean,
    val mode: GameMode,
    val size: Int,
    val autoXEnabled: Boolean,
    val speedDecrees: Boolean,
    val theme: AppTheme,
    val currentDecree: Decree?,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val shakeToken: Int,
    val decreePulse: Int,
    val lastPlaced: GridPos?,
    val roundTag: String,
    val dailyLabel: String,
) {
    val placedCount: Int get() = analysis.queens.size
    val fogActive: Boolean get() = currentDecree == Decree.FOG_OF_WAR

    fun display(pos: GridPos): CellDisplay = cells[pos].display
    fun isAutoX(pos: GridPos): Boolean = cells[pos].isAutoX
    fun isGuard(pos: GridPos): Boolean = puzzle.guardPos == pos
    fun isRevealed(region: Int): Boolean = region in revealedRegions
    fun walls(pos: GridPos): Set<WallEdge> = BoardRules.walls(pos, puzzle, fogActive, revealedRegions)
}

class GameEngine(
    private val store: KeyValueStore,
    /** Monotonic milliseconds. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val today: () -> LocalDate = { LocalDate.now() },
    private val random: Random = Random.Default,
) {
    var onEvent: (GameEvent) -> Unit = {}

    private val stats = StatsStore(store)

    // region Game state

    var puzzle: Puzzle private set
    /**
     * The board. Each [Cell] carries a player-owned `base` plus the set of
     * Queens that have auto-marked an X on it (`autoOwners`).
     */
    var cells: Board private set
    var analysis: BoardAnalysis private set
    var revealedRegions: Set<Int> = emptySet(); private set
    var moves = 0; private set
    var isSolved = false; private set
    var isDaily = false; private set

    /** Bumped to play the board's shake animation (bad move / blocked square). */
    var shakeToken = 0; private set
    /** Bumped when a new decree is proclaimed (drives the banner pulse). */
    var decreePulse = 0; private set
    /** The square that most recently became a Queen (drives the pop animation). */
    var lastPlaced: GridPos? = null; private set

    // endregion

    // region Settings

    private var _mode: GameMode
    private var _size: Int
    private var _autoX: Boolean
    private var _speed: Boolean
    private var _theme: AppTheme

    var mode: GameMode
        get() = _mode
        set(value) {
            val old = _mode
            _mode = value
            store.putString(Keys.MODE, value.key)
            if (old != value) startNewGame(daily = false)
        }

    var size: Int
        get() = _size
        set(value) {
            val clamped = value.coerceIn(MIN_SIZE, MAX_SIZE)
            val old = _size
            _size = clamped
            store.putInt(Keys.SIZE, clamped)
            if (old != clamped) startNewGame(daily = false)
        }

    /** Smart Auto-X master switch — toggleable at any time from the header. */
    var autoXEnabled: Boolean
        get() = _autoX
        set(value) {
            val old = _autoX
            _autoX = value
            store.putBoolean(Keys.AUTO_X, value)
            if (old != value) reconcileAutoX()
        }

    var speedDecrees: Boolean
        get() = _speed
        set(value) {
            val old = _speed
            _speed = value
            store.putBoolean(Keys.SPEED, value)
            if (old != value) startNewGame(daily = false)
        }

    var theme: AppTheme
        get() = _theme
        set(value) {
            _theme = value
            store.putString(Keys.THEME, value.key)
        }

    // endregion

    // region Private

    private object Keys {
        const val MODE = "queens.mode"
        const val SIZE = "queens.size"
        const val AUTO_X = "queens.autoX"
        const val SPEED = "queens.speed"
        const val THEME = "queens.theme"
    }

    private var history: MutableList<Board> = mutableListOf()
    private var historyIndex = -1
    private var lastConflictCount = 0

    private var startStamp: Long? = null
    private var frozen = 0L
    private var speedIndex = 0
    private var nextRotationAt = ROTATION_MILLIS
    private var dailyKey: String? = null

    // Drag-to-mark session
    private enum class DragKind { MARK, ERASE }
    private var dragActive = false
    private var dragKind: DragKind? = null
    private var dragChanges = 0

    // endregion

    init {
        _mode = GameMode.fromKey(store.getString(Keys.MODE)) ?: GameMode.STANDARD
        _size = (store.getInt(Keys.SIZE) ?: 8).coerceIn(MIN_SIZE, MAX_SIZE)
        _autoX = store.getBoolean(Keys.AUTO_X) ?: false
        _speed = store.getBoolean(Keys.SPEED) ?: false
        _theme = AppTheme.fromKey(store.getString(Keys.THEME)) ?: AppTheme.SYSTEM

        puzzle = PuzzleGenerator.generate(_size, RuleSet(), randomSeed())
        cells = emptyBoard(_size)
        analysis = BoardRules.analyse(cells, puzzle)

        startNewGame(daily = false)
    }

    private fun randomSeed(): ULong = random.nextLong().toULong()

    // region Round lifecycle

    fun startNewGame(daily: Boolean) {
        isDaily = daily
        val n = size

        var rules = RuleSet()
        var chosenDecree: Decree? = null
        val seed: ULong

        if (daily) {
            seed = PuzzleGenerator.dailySeed(today(), n)
            dailyKey = PuzzleGenerator.dailyKey(today(), n)
        } else {
            seed = randomSeed()
            dailyKey = null
            if (mode == GameMode.DECREES) {
                if (speedDecrees) {
                    chosenDecree = Decree.TRUCE
                    speedIndex = 0
                } else {
                    val pickRng = SeededRng(seed xor SeededRng.GOLDEN)
                    chosenDecree = listOf(
                        Decree.FOG_OF_WAR, Decree.ROYAL_GUARD, Decree.ASSASSINS_RANGE, Decree.COLOR_LOCK,
                    ).randomElementSwift(pickRng) ?: Decree.FOG_OF_WAR
                    rules = RuleSet(diagonalTwo = chosenDecree == Decree.ASSASSINS_RANGE)
                }
            }
        }

        var generated = PuzzleGenerator.generate(n, rules, seed).copy(decree = chosenDecree)
        if (chosenDecree == Decree.ROYAL_GUARD) {
            generated = generated.copy(guardPos = PuzzleGenerator.pickGuard(generated, seed))
        }
        puzzle = generated

        cells = emptyBoard(n)
        revealedRegions = if (chosenDecree == Decree.FOG_OF_WAR) emptySet() else (0 until n).toSet()
        history = mutableListOf(cells)
        historyIndex = 0
        moves = 0
        frozen = 0
        isSolved = false
        lastPlaced = null
        lastConflictCount = 0
        nextRotationAt = ROTATION_MILLIS

        recompute()
        startStamp = clock()

        if (mode == GameMode.DECREES && !daily) decreePulse += 1
    }

    // endregion

    // region Derived helpers

    val currentDecree: Decree? get() = if (mode == GameMode.DECREES) puzzle.decree else null
    val placedCount: Int get() = analysis.queens.size
    val canUndo: Boolean get() = historyIndex > 0
    val canRedo: Boolean get() = historyIndex < history.size - 1

    val elapsedMillis: Long get() = frozen + (startStamp?.let { clock() - it } ?: 0L)

    /** Speed decrees rotate every 30 s of play in a random Royal Decrees round. */
    val rotationActive: Boolean
        get() = mode == GameMode.DECREES && speedDecrees && !isDaily && !isSolved

    val dailyLabel: String
        get() = if (stats.isDailyDone(PuzzleGenerator.dailyKey(today(), size))) "Daily ✓" else "Daily"

    val roundTag: String
        get() {
            if (isDaily) return "Daily · ${PuzzleGenerator.dailyKey(today(), puzzle.size)}"
            val decree = currentDecree
            if (decree != null && decree != Decree.TRUCE) return decree.title
            return "Standard · ${puzzle.size}×${puzzle.size}"
        }

    fun display(pos: GridPos): CellDisplay = cells[pos].display
    fun base(pos: GridPos): CellBase = cells[pos].base
    /** True when the X shown here is purely auto-generated (drives the faint style). */
    fun isAutoX(pos: GridPos): Boolean = cells[pos].isAutoX
    fun isGuard(pos: GridPos): Boolean = puzzle.guardPos == pos

    fun snapshot(): GameSnapshot = GameSnapshot(
        puzzle = puzzle,
        cells = cells,
        analysis = analysis,
        revealedRegions = revealedRegions,
        moves = moves,
        elapsedSeconds = (elapsedMillis / 1000).toInt(),
        isSolved = isSolved,
        isDaily = isDaily,
        mode = mode,
        size = size,
        autoXEnabled = autoXEnabled,
        speedDecrees = speedDecrees,
        theme = theme,
        currentDecree = currentDecree,
        canUndo = canUndo,
        canRedo = canRedo,
        shakeToken = shakeToken,
        decreePulse = decreePulse,
        lastPlaced = lastPlaced,
        roundTag = roundTag,
        dailyLabel = dailyLabel,
    )

    // endregion

    // region Input: tap / long-press

    /**
     * Cycle: empty → manual × → Queen → empty.
     * Tapping a square that only shows an *auto* X claims it as a manual mark
     * (so it survives the owning Queen being removed).
     */
    fun tap(pos: GridPos) {
        if (isSolved) return
        if (isGuard(pos)) return bump()

        when (cells[pos].base) {
            CellBase.QUEEN -> {
                setBase(pos, CellBase.EMPTY)
                stripAutoX(owner = pos)
            }
            CellBase.MANUAL_X -> {
                setBase(pos, CellBase.QUEEN)
                applyAutoX(owner = pos)
            }
            CellBase.EMPTY -> setBase(pos, CellBase.MANUAL_X)
        }

        reveal(pos)
        moves += 1
        commit(placedQueenAt = if (cells[pos].base == CellBase.QUEEN) pos else null)
    }

    /** Long-press shortcut — drop (or lift) a Queen directly. */
    fun placeQueen(pos: GridPos) {
        if (isSolved) return
        if (isGuard(pos)) return bump()

        if (cells[pos].base == CellBase.QUEEN) {
            setBase(pos, CellBase.EMPTY)
            stripAutoX(owner = pos)
            reveal(pos)
            moves += 1
            commit(placedQueenAt = null)
        } else {
            setBase(pos, CellBase.QUEEN)
            applyAutoX(owner = pos)
            reveal(pos)
            moves += 1
            commit(placedQueenAt = pos)
        }
    }

    private fun setBase(pos: GridPos, base: CellBase) {
        cells = cells.update(pos) { it.copy(base = base) }
    }

    private fun commit(placedQueenAt: GridPos?) {
        pushHistory()
        recompute()
        if (placedQueenAt != null) {
            lastPlaced = placedQueenAt
            onEvent(GameEvent.Tap(0.8f))
        } else {
            onEvent(GameEvent.Tap(0.4f))
        }
    }

    // endregion

    // region Input: drag-to-mark

    fun handleDrag(start: GridPos?, current: GridPos?) {
        if (isSolved || current == null) return
        if (!dragActive) {
            dragActive = true
            val anchor = start ?: current
            // Erase only when the anchor is a *manual* mark; otherwise paint.
            dragKind = if (cells[anchor].base == CellBase.MANUAL_X) DragKind.ERASE else DragKind.MARK
            dragChanges = 0
            if (applyDragMark(anchor)) dragChanges += 1
        }
        if (applyDragMark(current)) dragChanges += 1
    }

    fun endDrag() {
        try {
            if (!dragActive || dragChanges <= 0) return
            moves += dragChanges
            pushHistory()
            recompute()
            onEvent(GameEvent.Tap(0.3f))
        } finally {
            dragActive = false; dragKind = null; dragChanges = 0
        }
    }

    private fun applyDragMark(pos: GridPos): Boolean {
        val kind = dragKind ?: return false
        if (isGuard(pos)) return false
        return when {
            kind == DragKind.MARK && cells[pos].base == CellBase.EMPTY -> {
                setBase(pos, CellBase.MANUAL_X); reveal(pos); true
            }
            kind == DragKind.ERASE && cells[pos].base == CellBase.MANUAL_X -> {
                setBase(pos, CellBase.EMPTY); reveal(pos); true
            }
            else -> false
        }
    }

    // endregion

    // region Smart Auto-X

    /** Whether an auto-fill should run at all right now. */
    private val autoFillActive: Boolean
        get() = autoXEnabled || puzzle.decree == Decree.COLOR_LOCK

    /**
     * Push [owner]'s id onto every *empty* target square's owner set.
     * Auto-X fills the 8 neighbours + rank + file + realm; when the master
     * switch is off but the Color Lock decree is in force, only the realm.
     */
    private fun applyAutoX(owner: GridPos) {
        if (!autoFillActive) return
        val targets = (if (autoXEnabled) {
            BoardRules.autoXTargets(owner, puzzle)
        } else {
            BoardRules.realmTargets(owner, puzzle)
        }).toSet()
        cells = cells.mapIndexed { r, row ->
            row.mapIndexed { c, cell ->
                if (GridPos(r, c) in targets && cell.base == CellBase.EMPTY) {
                    cell.copy(autoOwners = cell.autoOwners + owner)
                } else {
                    cell
                }
            }
        }
    }

    /**
     * Strip [owner]'s id from every square. Squares whose owner set empties
     * (and that were never manually marked) fall back to empty automatically.
     */
    private fun stripAutoX(owner: GridPos) {
        cells = cells.map { row ->
            row.map { cell -> if (owner in cell.autoOwners) cell.copy(autoOwners = cell.autoOwners - owner) else cell }
        }
    }

    /**
     * Rebuild every owner set from scratch off the current Queens + settings.
     * Used after a toggle, undo/redo, or Clear.
     */
    private fun recomputeAutoX() {
        cells = cells.map { row -> row.map { it.copy(autoOwners = emptySet()) } }
        if (!autoFillActive) return
        for (r in 0 until puzzle.size) {
            for (c in 0 until puzzle.size) {
                if (cells[r][c].base == CellBase.QUEEN) applyAutoX(owner = GridPos(r, c))
            }
        }
    }

    private fun reconcileAutoX() {
        recomputeAutoX()
        recompute()
    }

    // endregion

    // region History

    fun undo() {
        if (!canUndo) return
        historyIndex -= 1
        cells = history[historyIndex]
        afterHistoryJump()
    }

    fun redo() {
        if (!canRedo) return
        historyIndex += 1
        cells = history[historyIndex]
        afterHistoryJump()
    }

    fun clearBoard() {
        val hasContent = cells.any { row -> row.any { it.base != CellBase.EMPTY || it.autoOwners.isNotEmpty() } }
        if (!hasContent) return
        cells = emptyBoard(puzzle.size)
        pushHistory()
        recompute()
        onEvent(GameEvent.Tap(0.5f))
    }

    private fun pushHistory() {
        if (historyIndex < history.size - 1) {
            history = history.subList(0, historyIndex + 1).toMutableList()
        }
        history.add(cells)
        historyIndex = history.size - 1
    }

    private fun afterHistoryJump() {
        if (isSolved) {
            isSolved = false
            resumeTimer()
        }
        // Owner sets are re-derived so they always match the live settings.
        recomputeAutoX()
        if (currentDecree == Decree.FOG_OF_WAR) {
            val touched = mutableSetOf<Int>()
            for (r in 0 until puzzle.size) {
                for (c in 0 until puzzle.size) {
                    if (cells[r][c].base != CellBase.EMPTY) touched.add(puzzle.regions[r][c])
                }
            }
            revealedRegions = revealedRegions + touched
        }
        recompute()
        onEvent(GameEvent.Tap(0.3f))
    }

    // endregion

    // region Validation + win

    private fun recompute() {
        analysis = BoardRules.analyse(cells, puzzle)

        if (analysis.conflicts.size > lastConflictCount) {
            shakeToken += 1
            onEvent(GameEvent.Warning)
        }
        lastConflictCount = analysis.conflicts.size

        if (analysis.solved && !isSolved) handleSolve()
    }

    private fun handleSolve() {
        isSolved = true
        pauseTimer()
        onEvent(GameEvent.Success)

        stats.recordSolve(puzzle.size, (elapsedMillis / 1000).toInt())
        if (isDaily) dailyKey?.let { stats.recordDaily(it) }
        onEvent(GameEvent.Solved)
    }

    private fun bump() {
        shakeToken += 1
        onEvent(GameEvent.Warning)
    }

    private fun reveal(pos: GridPos) {
        if (currentDecree != Decree.FOG_OF_WAR) return
        revealedRegions = revealedRegions + puzzle.region(pos)
    }

    // endregion

    // region Timer

    fun pauseTimer() {
        val start = startStamp ?: return
        frozen += clock() - start
        startStamp = null
    }

    fun resumeTimer() {
        if (startStamp != null || isSolved) return
        startStamp = clock()
    }

    /**
     * Called by the UI's ticker. Rotates the Speed decree once every
     * 30 seconds of (unpaused) play. Returns true when a decree changed.
     */
    fun tick(): Boolean {
        if (!rotationActive || startStamp == null) return false
        if (elapsedMillis < nextRotationAt) return false
        nextRotationAt += ROTATION_MILLIS
        rotateDecree()
        return true
    }

    // endregion

    // region Speed decree rotation

    fun rotateDecree() {
        if (isSolved || mode != GameMode.DECREES || !speedDecrees) return
        val sequence = listOf(Decree.FOG_OF_WAR, Decree.ROYAL_GUARD, Decree.COLOR_LOCK, Decree.TRUCE)
        speedIndex = (speedIndex + 1) % sequence.size
        val next = sequence[speedIndex]

        var updated = puzzle
        if (updated.decree == Decree.ROYAL_GUARD && next != Decree.ROYAL_GUARD) {
            updated = updated.copy(guardPos = null)
        }
        updated = updated.copy(decree = next)

        revealedRegions = if (next == Decree.FOG_OF_WAR) {
            val revealed = mutableSetOf<Int>()
            for (r in 0 until updated.size) {
                for (c in 0 until updated.size) {
                    if (cells[r][c].base != CellBase.EMPTY) revealed.add(updated.regions[r][c])
                }
            }
            revealed
        } else {
            (0 until updated.size).toSet()
        }

        if (next == Decree.ROYAL_GUARD && updated.guardPos == null) {
            updated = updated.copy(guardPos = liveGuard(updated))
        }

        puzzle = updated
        decreePulse += 1
        onEvent(GameEvent.Success)
        recompute()
    }

    private fun liveGuard(puzzle: Puzzle): GridPos? {
        repeat(600) {
            val r = random.nextInt(puzzle.size)
            val c = random.nextInt(puzzle.size)
            if (puzzle.solution[r] != c && cells[r][c].base == CellBase.EMPTY) return GridPos(r, c)
        }
        return null
    }

    // endregion

    /** Places the whole known solution — used by tests and debug builds. */
    fun fillSolution() {
        cells = emptyBoard(puzzle.size).mapIndexed { r, row ->
            row.mapIndexed { c, cell -> if (puzzle.solution[r] == c) cell.copy(base = CellBase.QUEEN) else cell }
        }
        recomputeAutoX()
        pushHistory()
        recompute()
    }

    companion object {
        const val MIN_SIZE = 6
        const val MAX_SIZE = 9
        const val ROTATION_MILLIS = 30_000L
        val SIZES = MIN_SIZE..MAX_SIZE
    }
}
