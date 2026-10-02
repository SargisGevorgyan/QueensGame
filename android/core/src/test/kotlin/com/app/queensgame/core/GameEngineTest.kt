package com.app.queensgame.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class GameEngineTest {

    private var now = 0L
    private val day = LocalDate.of(2026, 10, 2)

    private fun engine(store: KeyValueStore = InMemoryStore(), seed: Int = 1) =
        GameEngine(store, clock = { now }, today = { day }, random = Random(seed))

    // region Smart Auto-X

    @Test
    fun autoXFillsNeighboursAndPreservesManualMarks() {
        val vm = engine()
        vm.autoXEnabled = true

        // A deliberate manual mark next to where the Queen will go.
        vm.tap(GridPos(1, 1))
        assertEquals(CellBase.MANUAL_X, vm.base(GridPos(1, 1)))

        vm.placeQueen(GridPos(2, 2))
        assertEquals(CellDisplay.X, vm.display(GridPos(2, 3))) // auto-filled
        assertTrue(vm.isAutoX(GridPos(2, 3)))
        assertEquals(CellBase.MANUAL_X, vm.base(GridPos(1, 1))) // manual untouched
        assertFalse(vm.isAutoX(GridPos(1, 1)))

        // Remove the Queen — only its own auto marks disappear.
        vm.placeQueen(GridPos(2, 2))
        assertEquals(CellDisplay.EMPTY, vm.display(GridPos(2, 3)))
        assertEquals(CellBase.MANUAL_X, vm.base(GridPos(1, 1)))
    }

    @Test
    fun tappingAnAutoXPromotesItToManual() {
        val vm = engine()
        vm.autoXEnabled = true
        vm.placeQueen(GridPos(2, 2))

        val neighbour = GridPos(3, 3)
        assertTrue(vm.isAutoX(neighbour))
        vm.tap(neighbour) // claim it
        assertEquals(CellBase.MANUAL_X, vm.base(neighbour))

        vm.placeQueen(GridPos(2, 2)) // remove the Queen
        assertEquals(CellDisplay.X, vm.display(neighbour)) // the mark persists
        assertEquals(CellBase.MANUAL_X, vm.base(neighbour))
    }

    @Test
    fun autoXOwnershipIsSharedBetweenQueens() {
        val vm = engine()
        vm.autoXEnabled = true
        val shared = GridPos(3, 3) // touches (2,2) and (4,4)

        vm.placeQueen(GridPos(2, 2))
        assertTrue(vm.isAutoX(shared))
        vm.placeQueen(GridPos(4, 4))
        assertTrue(vm.isAutoX(shared))

        vm.placeQueen(GridPos(2, 2)) // remove one owner
        assertEquals("still owned by (4,4)", CellDisplay.X, vm.display(shared))
        vm.placeQueen(GridPos(4, 4)) // remove the other
        assertEquals(CellDisplay.EMPTY, vm.display(shared))
    }

    @Test
    fun togglingAutoXOffClearsAutoOnlyMarksAndBackOnReapplies() {
        val vm = engine()
        vm.autoXEnabled = true
        vm.tap(GridPos(0, 0)) // a manual X
        vm.placeQueen(GridPos(2, 2))
        val neighbour = GridPos(2, 3)
        assertTrue(vm.isAutoX(neighbour))

        vm.autoXEnabled = false
        assertEquals(CellDisplay.EMPTY, vm.display(neighbour)) // auto swept
        assertEquals(CellBase.MANUAL_X, vm.base(GridPos(0, 0))) // manual kept

        vm.autoXEnabled = true
        assertEquals(CellDisplay.X, vm.display(neighbour)) // re-applied
    }

    @Test
    fun autoXFillsRankFileAndRealm() {
        val vm = engine()
        vm.autoXEnabled = true
        val queen = GridPos(2, 2)
        vm.placeQueen(queen)
        assertEquals(CellDisplay.X, vm.display(GridPos(2, 6))) // same rank
        assertEquals(CellDisplay.X, vm.display(GridPos(6, 2))) // same file

        // Every empty realm-mate of the Queen is auto-marked.
        val region = vm.puzzle.region(queen)
        for (r in 0 until vm.puzzle.size) for (c in 0 until vm.puzzle.size) {
            val pos = GridPos(r, c)
            if (pos != queen && vm.puzzle.region(pos) == region) assertEquals(CellDisplay.X, vm.display(pos))
        }
    }

    // endregion

    // region Cycle / history

    @Test
    fun tapCyclesAndUndoRedoRestore() {
        val vm = engine()
        vm.autoXEnabled = false
        val p = GridPos(0, 0)

        vm.tap(p); assertEquals(CellDisplay.X, vm.display(p))
        vm.tap(p); assertEquals(CellDisplay.QUEEN, vm.display(p))
        assertEquals(2, vm.moves)

        vm.undo(); assertEquals(CellDisplay.X, vm.display(p))
        vm.undo(); assertEquals(CellDisplay.EMPTY, vm.display(p))
        assertFalse(vm.canUndo)
        vm.redo(); assertEquals(CellDisplay.X, vm.display(p))
        assertTrue(vm.canRedo)
    }

    @Test
    fun newMoveAfterUndoDropsTheRedoBranch() {
        val vm = engine()
        vm.tap(GridPos(0, 0))
        vm.tap(GridPos(0, 1))
        vm.undo()
        vm.tap(GridPos(5, 5))
        assertFalse(vm.canRedo)
        assertEquals(CellDisplay.EMPTY, vm.display(GridPos(0, 1)))
    }

    @Test
    fun clearBoardRemovesEveryMark() {
        val vm = engine()
        vm.autoXEnabled = true
        vm.placeQueen(GridPos(2, 2))
        vm.tap(GridPos(0, 0))
        vm.clearBoard()
        assertEquals(0, vm.placedCount)
        for (r in 0 until vm.puzzle.size) for (c in 0 until vm.puzzle.size) {
            assertEquals(CellDisplay.EMPTY, vm.display(GridPos(r, c)))
        }
        vm.undo()
        assertEquals(CellDisplay.X, vm.display(GridPos(0, 0)))
    }

    @Test
    fun dragPaintsThenErasesManualMarks() {
        val vm = engine()
        vm.handleDrag(GridPos(0, 0), GridPos(0, 1))
        vm.handleDrag(GridPos(0, 0), GridPos(0, 2))
        vm.endDrag()
        assertEquals(3, vm.moves)
        for (c in 0..2) assertEquals(CellBase.MANUAL_X, vm.base(GridPos(0, c)))

        // Dragging from a manual × erases.
        vm.handleDrag(GridPos(0, 0), GridPos(0, 1))
        vm.endDrag()
        assertEquals(CellBase.EMPTY, vm.base(GridPos(0, 0)))
        assertEquals(CellBase.EMPTY, vm.base(GridPos(0, 1)))
        assertEquals(CellBase.MANUAL_X, vm.base(GridPos(0, 2)))

        vm.undo()
        assertEquals(CellBase.MANUAL_X, vm.base(GridPos(0, 0)))
    }

    // endregion

    // region Win, stats, timer

    @Test
    fun solvingTriggersWinAndRecordsStats() {
        val store = InMemoryStore()
        val vm = engine(store)
        val events = mutableListOf<GameEvent>()
        vm.onEvent = { events += it }

        now += 42_000
        vm.fillSolution()
        assertTrue(vm.isSolved)
        assertTrue(vm.analysis.solved)
        assertTrue(GameEvent.Solved in events)

        val stats = StatsStore(store)
        assertEquals(1, stats.totalSolved)
        assertEquals(42, stats.bestSeconds(vm.size))

        // The clock stops once solved.
        now += 10_000
        assertEquals(42_000L, vm.elapsedMillis)

        // Further input is ignored until the player undoes.
        vm.tap(GridPos(0, 0))
        assertEquals(vm.puzzle.solution[0] == 0, vm.display(GridPos(0, 0)) == CellDisplay.QUEEN)
        vm.undo()
        assertFalse(vm.isSolved)
    }

    @Test
    fun dailyRoundIsReproducibleAndMarkedDone() {
        val store = InMemoryStore()
        val a = engine(store, seed = 1)
        a.startNewGame(daily = true)
        val b = engine(InMemoryStore(), seed = 2)
        b.startNewGame(daily = true)
        assertEquals(a.puzzle.regions, b.puzzle.regions)
        assertEquals(a.puzzle.solution, b.puzzle.solution)

        assertEquals("Daily", a.dailyLabel)
        a.fillSolution()
        assertEquals("Daily ✓", a.dailyLabel)
        assertTrue(a.roundTag.startsWith("Daily · 2026-10-02|"))
    }

    @Test
    fun pausedTimeIsNotCounted() {
        val vm = engine()
        now += 5_000
        vm.pauseTimer()
        now += 60_000
        vm.resumeTimer()
        now += 1_000
        assertEquals(6, vm.snapshot().elapsedSeconds)
    }

    // endregion

    // region Settings + decrees

    @Test
    fun settingsPersistAcrossEngines() {
        val store = InMemoryStore()
        val vm = engine(store)
        vm.size = 6
        vm.autoXEnabled = true
        vm.theme = AppTheme.DARK
        vm.mode = GameMode.DECREES

        val again = engine(store)
        assertEquals(6, again.size)
        assertEquals(6, again.puzzle.size)
        assertTrue(again.autoXEnabled)
        assertEquals(AppTheme.DARK, again.theme)
        assertEquals(GameMode.DECREES, again.mode)
    }

    @Test
    fun sizeIsClampedToSupportedBoards() {
        val vm = engine()
        vm.size = 12
        assertEquals(9, vm.size)
        vm.size = 2
        assertEquals(6, vm.size)
    }

    @Test
    fun decreesModeProclaimsADecree() {
        val vm = engine()
        vm.mode = GameMode.DECREES
        val decree = vm.currentDecree
        assertNotNull(decree)
        assertTrue(decree != Decree.TRUCE)
        when (decree) {
            Decree.ROYAL_GUARD -> assertNotNull(vm.puzzle.guardPos)
            Decree.FOG_OF_WAR -> assertTrue(vm.revealedRegions.isEmpty())
            Decree.ASSASSINS_RANGE -> assertTrue(vm.puzzle.ruleSet.diagonalTwo)
            else -> Unit
        }
        vm.mode = GameMode.STANDARD
        assertNull(vm.currentDecree)
    }

    @Test
    fun royalGuardBlocksItsSquare() {
        // Find a seed whose first decree round is the Royal Guard.
        for (seed in 1..200) {
            val vm = engine(seed = seed)
            vm.mode = GameMode.DECREES
            val guard = vm.puzzle.guardPos ?: continue
            val shake = vm.shakeToken
            vm.tap(guard)
            assertEquals(CellDisplay.EMPTY, vm.display(guard))
            assertEquals(shake + 1, vm.shakeToken)
            assertEquals(0, vm.moves)
            return
        }
        throw AssertionError("no Royal Guard round in 200 seeds")
    }

    @Test
    fun colorLockMarksRealmEvenWithAutoXOff() {
        for (seed in 1..200) {
            val vm = engine(seed = seed)
            vm.mode = GameMode.DECREES
            if (vm.currentDecree != Decree.COLOR_LOCK) continue
            vm.autoXEnabled = false
            val queen = GridPos(0, 0)
            vm.placeQueen(queen)
            val region = vm.puzzle.region(queen)
            for (r in 0 until vm.puzzle.size) for (c in 0 until vm.puzzle.size) {
                val pos = GridPos(r, c)
                if (pos == queen) continue
                val expected = if (vm.puzzle.region(pos) == region) CellDisplay.X else CellDisplay.EMPTY
                assertEquals(expected, vm.display(pos))
            }
            return
        }
        throw AssertionError("no Color Lock round in 200 seeds")
    }

    @Test
    fun fogRevealsRealmsOnTouch() {
        for (seed in 1..200) {
            val vm = engine(seed = seed)
            vm.mode = GameMode.DECREES
            if (vm.currentDecree != Decree.FOG_OF_WAR) continue
            val pos = GridPos(1, 1)
            vm.tap(pos)
            assertEquals(setOf(vm.puzzle.region(pos)), vm.revealedRegions)
            return
        }
        throw AssertionError("no Fog of War round in 200 seeds")
    }

    @Test
    fun speedDecreesRotateEveryThirtySecondsOfPlay() {
        val vm = engine()
        vm.mode = GameMode.DECREES
        vm.speedDecrees = true
        assertEquals(Decree.TRUCE, vm.currentDecree)

        now += 29_000
        assertFalse(vm.tick())
        now += 1_000
        assertTrue(vm.tick())
        // Same order as iOS: the round opens on a Truce, then Guard, Lock, Truce, Fog…
        assertEquals(Decree.ROYAL_GUARD, vm.currentDecree)
        assertNotNull(vm.puzzle.guardPos)

        // Paused time doesn't advance the herald.
        vm.pauseTimer()
        now += 120_000
        assertFalse(vm.tick())
        vm.resumeTimer()
        now += 30_000
        assertTrue(vm.tick())
        assertEquals(Decree.COLOR_LOCK, vm.currentDecree)
        assertNull(vm.puzzle.guardPos)

        now += 30_000
        assertTrue(vm.tick())
        assertEquals(Decree.TRUCE, vm.currentDecree)

        now += 30_000
        assertTrue(vm.tick())
        assertEquals(Decree.FOG_OF_WAR, vm.currentDecree)
        assertTrue(vm.revealedRegions.isEmpty())
    }

    // endregion
}
