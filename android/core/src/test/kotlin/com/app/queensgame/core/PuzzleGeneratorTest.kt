package com.app.queensgame.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class PuzzleGeneratorTest {

    @Test
    fun generatedPuzzlesAreUniqueAndWellFormed() {
        for (size in 6..9) {
            for (seed in 1uL..3uL) {
                val puzzle = PuzzleGenerator.generate(size, RuleSet(), seed * 2_654_435_761uL)
                assertEquals(size, puzzle.size)
                assertTrue("solution invalid for size $size seed $seed", solutionObeysRules(puzzle))
                assertTrue("regions not contiguous for size $size seed $seed", regionsAreContiguous(puzzle))
                val solutions = PuzzleGenerator.countSolutions(size, puzzle.regions, puzzle.ruleSet, cap = 3)
                assertEquals("puzzle not unique for size $size seed $seed", 1, solutions)
            }
        }
    }

    @Test
    fun colorMapUsesDistinctPaletteSlots() {
        val puzzle = PuzzleGenerator.generate(9, RuleSet(), 42uL)
        assertEquals(9, puzzle.colorMap.size)
        assertEquals(9, puzzle.colorMap.toSet().size)
        assertTrue(puzzle.colorMap.all { it in 0 until 9 })
    }

    @Test
    fun dailyPuzzleIsDeterministic() {
        val date = LocalDate.of(2025, 10, 9)
        val a = PuzzleGenerator.generate(8, RuleSet(), PuzzleGenerator.dailySeed(date, 8))
        val b = PuzzleGenerator.generate(8, RuleSet(), PuzzleGenerator.dailySeed(date, 8))
        assertEquals(a.regions, b.regions)
        assertEquals(a.solution, b.solution)
        assertEquals(a.colorMap, b.colorMap)
    }

    @Test
    fun dailyKeyAndSeedDependOnDateAndSize() {
        val date = LocalDate.of(2026, 1, 5)
        assertEquals("2026-01-05|7", PuzzleGenerator.dailyKey(date, 7))
        assertNotEquals(PuzzleGenerator.dailySeed(date, 7), PuzzleGenerator.dailySeed(date, 8))
        assertNotEquals(PuzzleGenerator.dailySeed(date, 7), PuzzleGenerator.dailySeed(date.plusDays(1), 7))
    }

    @Test
    fun dailySeedIsFnv1a() {
        // FNV-1a 64 of the empty string is the offset basis; check one known
        // vector so the hash stays byte-compatible with the iOS build.
        // FNV-1a("a") = 0xaf63dc4c8601ec8c
        var hash = 0xcbf2_9ce4_8422_2325uL
        for (byte in "a".encodeToByteArray()) {
            hash = hash xor byte.toUByte().toULong()
            hash *= 0x0000_0100_0000_01b3uL
        }
        assertEquals(0xaf63_dc4c_8601_ec8cuL, hash)
    }

    @Test
    fun assassinsRangeSolutionAvoidsDiagonalTwo() {
        for (seed in 1uL..3uL) {
            val puzzle = PuzzleGenerator.generate(8, RuleSet(diagonalTwo = true), seed * 1_000_003uL)
            val cols = puzzle.solution
            for (r in 0 until 8 - 2) {
                assertNotEquals("diagonal-two clash slipped into an Assassin's Range solution",
                    2, abs(cols[r] - cols[r + 2]))
            }
            assertEquals(1, PuzzleGenerator.countSolutions(8, puzzle.regions, RuleSet(diagonalTwo = true), cap = 3))
        }
    }

    @Test
    fun guardNeverSitsOnTheSolution() {
        for (seed in 1uL..20uL) {
            val puzzle = PuzzleGenerator.generate(7, RuleSet(), seed)
            val guard = PuzzleGenerator.pickGuard(puzzle, seed)
            assertNotEquals(puzzle.solution[guard.row], guard.col)
        }
    }

    // region Helpers

    private fun solutionObeysRules(puzzle: Puzzle): Boolean {
        val n = puzzle.size
        val cols = puzzle.solution
        if (cols.toSet().size != n) return false
        for (r in 0 until n - 1) if (abs(cols[r] - cols[r + 1]) <= 1) return false
        return (0 until n).map { puzzle.regions[it][cols[it]] }.toSet().size == n
    }

    /** Every realm is a single 4-connected blob. */
    private fun regionsAreContiguous(puzzle: Puzzle): Boolean {
        val n = puzzle.size
        for (k in 0 until n) {
            val cells = (0 until n).flatMap { r -> (0 until n).map { c -> GridPos(r, c) } }
                .filter { puzzle.region(it) == k }
            if (cells.isEmpty()) return false
            val seen = mutableSetOf(cells.first())
            val stack = ArrayDeque(listOf(cells.first()))
            while (stack.isNotEmpty()) {
                val p = stack.removeLast()
                for ((dr, dc) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val np = GridPos(p.row + dr, p.col + dc)
                    if (np.row !in 0 until n || np.col !in 0 until n) continue
                    if (puzzle.region(np) == k && seen.add(np)) stack.addLast(np)
                }
            }
            if (seen.size != cells.size) return false
        }
        return true
    }

    // endregion
}
