package com.app.queensgame.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BoardRulesTest {

    private fun board(n: Int): Array<Array<Cell>> = Array(n) { Array(n) { Cell() } }
    private fun Array<Array<Cell>>.toBoard(): Board = map { it.toList() }

    @Test
    fun duplicateInRowIsFlagged() {
        val puzzle = PuzzleGenerator.generate(6, RuleSet(), 99uL)
        val cells = board(6)
        cells[0][0] = Cell(CellBase.QUEEN)
        cells[0][4] = Cell(CellBase.QUEEN)
        val a = BoardRules.analyse(cells.toBoard(), puzzle)
        assertTrue(GridPos(0, 0) in a.conflicts)
        assertTrue(GridPos(0, 4) in a.conflicts)
        assertFalse(a.solved)
    }

    @Test
    fun touchingQueensAreFlagged() {
        val puzzle = PuzzleGenerator.generate(6, RuleSet(), 7uL)
        val cells = board(6)
        cells[1][1] = Cell(CellBase.QUEEN)
        cells[2][2] = Cell(CellBase.QUEEN)
        assertEquals(2, BoardRules.analyse(cells.toBoard(), puzzle).conflicts.size)
    }

    @Test
    fun assassinsRangeFlagsDiagonalTwo() {
        val puzzle = PuzzleGenerator.generate(7, RuleSet(diagonalTwo = true), 11uL)
        val cells = board(7)
        cells[1][1] = Cell(CellBase.QUEEN)
        cells[3][3] = Cell(CellBase.QUEEN)
        val a = BoardRules.analyse(cells.toBoard(), puzzle)
        assertTrue(GridPos(1, 1) in a.conflicts)

        val standard = puzzle.copy(ruleSet = RuleSet(diagonalTwo = false))
        val b = BoardRules.analyse(cells.toBoard(), standard)
        // Only realm duplicates could still clash; the diagonal-two pair itself must not.
        val sameRealm = puzzle.region(GridPos(1, 1)) == puzzle.region(GridPos(3, 3))
        assertEquals(sameRealm, b.conflicts.isNotEmpty())
    }

    @Test
    fun placingFullSolutionSolvesTheBoard() {
        val puzzle = PuzzleGenerator.generate(8, RuleSet(), 2024uL)
        val cells = board(8)
        for (r in 0 until 8) cells[r][puzzle.solution[r]] = Cell(CellBase.QUEEN)
        val a = BoardRules.analyse(cells.toBoard(), puzzle)
        assertTrue(a.conflicts.isEmpty())
        assertTrue(a.solved)
        assertTrue(a.rowDone.all { it })
        assertTrue(a.colDone.all { it })
        assertTrue(a.regionDone.all { it })
    }

    @Test
    fun autoXTargetsCoverNeighboursRankFileAndRealm() {
        val puzzle = PuzzleGenerator.generate(8, RuleSet(), 3uL)
        val queen = GridPos(3, 3)
        val targets = BoardRules.autoXTargets(queen, puzzle).toSet()

        assertTrue(GridPos(2, 2) in targets) // diagonal neighbour
        assertTrue(GridPos(4, 4) in targets) // diagonal neighbour
        assertTrue(GridPos(3, 7) in targets) // same rank
        assertTrue(GridPos(7, 3) in targets) // same file

        val region = puzzle.region(queen)
        for (r in 0 until 8) for (c in 0 until 8) {
            val pos = GridPos(r, c)
            val neighbour = abs(r - 3) <= 1 && abs(c - 3) <= 1
            val line = r == 3 || c == 3
            val realm = puzzle.region(pos) == region
            if (realm) assertTrue(pos in targets)
            if (!neighbour && !line && !realm) assertFalse(pos in targets)
        }
    }

    @Test
    fun wallsFollowRealmBoundariesAndFog() {
        val puzzle = PuzzleGenerator.generate(6, RuleSet(), 17uL)
        val corner = GridPos(0, 0)
        val walls = BoardRules.walls(corner, puzzle, fogged = false, revealed = emptySet())
        assertTrue(WallEdge.TOP in walls)
        assertTrue(WallEdge.LEADING in walls)
        assertEquals(puzzle.region(GridPos(1, 0)) != puzzle.region(corner), WallEdge.BOTTOM in walls)

        assertTrue(BoardRules.walls(corner, puzzle, fogged = true, revealed = emptySet()).isEmpty())
    }

    @Test
    fun romanNumerals() {
        assertEquals(listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"), (1..9).map(::roman))
    }
}
