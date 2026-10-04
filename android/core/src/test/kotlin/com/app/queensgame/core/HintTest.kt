package com.app.queensgame.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class HintTest {

    private val day = LocalDate.of(2026, 10, 4)

    private fun engine(seed: Int = 1) =
        GameEngine(InMemoryStore(), clock = { 0L }, today = { day }, random = Random(seed))

    // region Engine hints

    @Test
    fun hintPlacesACorrectQueen() {
        val vm = engine()
        assertTrue(vm.applyHint())
        assertNotNull(vm.hintedCell)
        val pos = vm.hintedCell!!
        assertEquals(CellBase.QUEEN, vm.base(pos))
        assertEquals(vm.puzzle.solution[pos.row], pos.col)
        assertEquals(1, vm.placedCount)
    }

    @Test
    fun hintLiftsAMisplacedQueenFirst() {
        val vm = engine()
        val wrong = GridPos(0, (vm.puzzle.solution[0] + 1) % vm.puzzle.size)
        vm.placeQueen(wrong)
        assertTrue(vm.applyHint())
        assertEquals(wrong, vm.hintedCell)
        assertEquals(CellBase.EMPTY, vm.base(wrong))
        assertEquals(0, vm.placedCount)
    }

    @Test
    fun hintsAloneSolveThePuzzleThenStop() {
        val vm = engine()
        repeat(vm.puzzle.size) { assertTrue(vm.applyHint()) }
        assertTrue(vm.isSolved)
        assertFalse(vm.applyHint())
    }

    @Test
    fun nextMoveClearsTheHintGlow() {
        val vm = engine()
        vm.applyHint()
        vm.undo()
        assertEquals(null, vm.hintedCell)
    }

    // endregion

    // region Wallet

    @Test
    fun walletStartsWithFreeHintsAndRefillsDaily() {
        val store = InMemoryStore()
        var today = day
        val wallet = HintWallet(store) { today }
        assertEquals(HintWallet.FREE_CAP, wallet.balance)
        repeat(HintWallet.FREE_CAP) { assertTrue(wallet.spend()) }
        assertFalse(wallet.spend())

        assertFalse(wallet.refillIfNewDay()) // same day
        assertEquals(0, wallet.balance)

        today = day.plusDays(1)
        val reopened = HintWallet(store) { today }
        assertEquals(1, reopened.balance)
        reopened.credit(10)
        assertEquals(11, HintWallet(store) { today }.balance)
    }

    @Test
    fun dailyRefillNeverExceedsFreeCap() {
        val store = InMemoryStore()
        HintWallet(store) { day }
        assertEquals(HintWallet.FREE_CAP, HintWallet(store) { day.plusDays(5) }.balance)
    }

    // endregion
}
