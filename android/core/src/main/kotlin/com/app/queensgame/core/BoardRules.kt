//
//  BoardRules.kt
//  QueensGame (Android)
//
//  Pure functions that read the board + puzzle and report conflicts,
//  completion and the "auto-X" overlay. No Android, fully testable.
//

package com.app.queensgame.core

import kotlin.math.abs
import kotlin.math.max

object BoardRules {

    /** Validate the current board against the puzzle's rules. */
    fun analyse(cells: Board, puzzle: Puzzle): BoardAnalysis {
        val n = puzzle.size
        val queens = mutableListOf<GridPos>()
        val byRow = IntArray(n)
        val byCol = IntArray(n)
        val byRegion = IntArray(n)

        for (r in 0 until n) {
            for (c in 0 until n) {
                if (cells[r][c].base != CellBase.QUEEN) continue
                queens.add(GridPos(r, c))
                byRow[r] += 1
                byCol[c] += 1
                byRegion[puzzle.regions[r][c]] += 1
            }
        }

        val conflicts = mutableSetOf<GridPos>()

        // Pairwise adjacency (and the Assassin's Range diagonal-two clash).
        for (i in queens.indices) {
            for (j in i + 1 until queens.size) {
                val a = queens[i]
                val b = queens[j]
                val dr = abs(a.row - b.row)
                val dc = abs(a.col - b.col)
                var clash = max(dr, dc) == 1
                if (puzzle.ruleSet.diagonalTwo && dr == 2 && dc == 2) clash = true
                if (clash) { conflicts.add(a); conflicts.add(b) }
            }
        }

        // Duplicates in a rank / file / realm.
        for (q in queens) {
            if (byRow[q.row] > 1 || byCol[q.col] > 1 || byRegion[puzzle.region(q)] > 1) {
                conflicts.add(q)
            }
        }

        val rowDone = BooleanArray(n)
        val colDone = BooleanArray(n)
        val regionDone = BooleanArray(n)
        for (q in queens) {
            if (q in conflicts) continue
            if (byRow[q.row] == 1) rowDone[q.row] = true
            if (byCol[q.col] == 1) colDone[q.col] = true
            val region = puzzle.region(q)
            if (byRegion[region] == 1) regionDone[region] = true
        }

        return BoardAnalysis(
            queens = queens,
            conflicts = conflicts,
            rowDone = rowDone.toList(),
            colDone = colDone.toList(),
            regionDone = regionDone.toList(),
            solved = queens.size == n && conflicts.isEmpty(),
        )
    }

    /**
     * Every square a Queen at [queen] should auto-mark with an X:
     * the 8 adjacent squares, the whole rank & file, and the whole colour
     * realm. (The caller only writes into squares that are currently empty;
     * duplicates in this list are harmless.)
     */
    fun autoXTargets(queen: GridPos, puzzle: Puzzle): List<GridPos> {
        val n = puzzle.size
        val targets = mutableListOf<GridPos>()

        // 8 adjacent
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = queen.row + dr
                val c = queen.col + dc
                if (r >= 0 && c >= 0 && r < n && c < n) targets.add(GridPos(r, c))
            }
        }
        // whole rank & file
        for (i in 0 until n) {
            targets.add(GridPos(queen.row, i))
            targets.add(GridPos(i, queen.col))
        }
        // whole colour realm
        targets.addAll(realmTargets(queen, puzzle))
        return targets
    }

    /**
     * Just the colour realm of [queen] — used by the Color Lock decree when
     * the Auto-X master switch is off.
     */
    fun realmTargets(queen: GridPos, puzzle: Puzzle): List<GridPos> {
        val n = puzzle.size
        val region = puzzle.region(queen)
        val targets = mutableListOf<GridPos>()
        for (r in 0 until n) {
            for (c in 0 until n) {
                if (puzzle.regions[r][c] == region) targets.add(GridPos(r, c))
            }
        }
        return targets
    }

    /**
     * Which sides of [pos] sit on a realm boundary. Under the Fog of War,
     * hidden realms draw no walls and a revealed realm is walled off from
     * hidden neighbours.
     */
    fun walls(pos: GridPos, puzzle: Puzzle, fogged: Boolean, revealed: Set<Int>): Set<WallEdge> {
        val n = puzzle.size
        val region = puzzle.region(pos)
        if (fogged && region !in revealed) return emptySet()

        fun boundary(r: Int, c: Int): Boolean {
            if (r < 0 || c < 0 || r >= n || c >= n) return true
            val other = puzzle.regions[r][c]
            if (other != region) return true
            if (fogged && other !in revealed) return true
            return false
        }

        val edges = mutableSetOf<WallEdge>()
        if (boundary(pos.row - 1, pos.col)) edges.add(WallEdge.TOP)
        if (boundary(pos.row + 1, pos.col)) edges.add(WallEdge.BOTTOM)
        if (boundary(pos.row, pos.col - 1)) edges.add(WallEdge.LEADING)
        if (boundary(pos.row, pos.col + 1)) edges.add(WallEdge.TRAILING)
        return edges
    }
}
