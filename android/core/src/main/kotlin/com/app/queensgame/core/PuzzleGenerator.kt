//
//  PuzzleGenerator.kt
//  QueensGame (Android)
//
//  Builds an N×N Queens puzzle with contiguous colour regions and a
//  *verified unique* solution, driven by a seeded RNG so "Daily" puzzles
//  are reproducible. A line-for-line port of the iOS `PuzzleGenerator.swift`.
//
//  Pipeline:
//    1. Backtrack a Queen placement (one per row & column, none touching).
//    2. Flood-grow one colour region from each Queen until the board is full.
//    3. Count solutions under the active rules — keep only when exactly one.
//

package com.app.queensgame.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

object PuzzleGenerator {

    private val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    // region Public entry

    fun generate(size: Int, rules: RuleSet, seed: ULong): Puzzle {
        val n = size
        val rng = SeededRng(seed)
        var lastRegions: List<List<Int>> = emptyList()
        var lastSolution: List<Int> = (0 until n).toList()

        repeat(30) {
            val solution = buildSolution(n, rules, rng) ?: return@repeat
            repeat(12) {
                val regions = growRegions(n, solution, rng)
                lastRegions = regions.map { it.toList() }
                lastSolution = solution
                if (repairToUnique(regions, solution, rules, rng)) {
                    return Puzzle(
                        size = n,
                        regions = regions.map { it.toList() },
                        solution = solution,
                        ruleSet = rules,
                        colorMap = colorMap(n, rng),
                    )
                }
            }
        }

        // Extremely unlikely fallback: ship the best board we managed to build.
        val regions = if (lastRegions.isEmpty()) {
            growRegions(n, lastSolution, rng).map { it.toList() }
        } else {
            lastRegions
        }
        return Puzzle(
            size = n,
            regions = regions,
            solution = lastSolution,
            ruleSet = rules,
            colorMap = colorMap(n, rng),
        )
    }

    // endregion

    /**
     * Reshapes [regions] (contiguity preserved) until the puzzle's only
     * solution is [solution]. Returns `false` if it gets stuck.
     */
    private fun repairToUnique(
        regions: Array<IntArray>,
        solution: List<Int>,
        rules: RuleSet,
        rng: SeededRng,
    ): Boolean {
        val n = solution.size

        repeat(300) {
            val alt = firstAlternate(n, regions, rules, solution) ?: return true

            val diffRows = (0 until n).filter { alt[it] != solution[it] }.toMutableList()
            diffRows.shuffleSwift(rng)

            var moved = false
            rowLoop@ for (r in diffRows) {
                val vr = r
                val vc = alt[r]
                val from = regions[vr][vc]

                val targets = mutableListOf<Int>()
                for ((dr, dc) in DIRS) {
                    val nr = vr + dr
                    val nc = vc + dc
                    if (nr < 0 || nc < 0 || nr >= n || nc >= n) continue
                    val other = regions[nr][nc]
                    if (other != from) targets.add(other)
                }
                targets.shuffleSwift(rng)

                for (to in targets) {
                    regions[vr][vc] = to
                    if (regionContiguous(regions, from, n)) {
                        moved = true
                        break@rowLoop
                    }
                    regions[vr][vc] = from // revert, try next
                }
            }

            if (!moved) return false
        }
        return firstAlternate(n, regions, rules, solution) == null
    }

    /** First complete placement that isn't [solution], or `null` if none exists. */
    private fun firstAlternate(
        n: Int,
        regions: Array<IntArray>,
        rules: RuleSet,
        solution: List<Int>,
    ): List<Int>? {
        val col = IntArray(n) { -1 }
        val usedCol = BooleanArray(n)
        val usedRegion = BooleanArray(n)
        var found: List<Int>? = null

        fun ok(r: Int, c: Int): Boolean {
            if (r > 0 && col[r - 1] >= 0 && abs(col[r - 1] - c) <= 1) return false
            if (rules.diagonalTwo && r > 1 && col[r - 2] >= 0 && abs(col[r - 2] - c) == 2) return false
            return true
        }

        fun recurse(r: Int) {
            if (found != null) return
            if (r == n) {
                val placement = col.toList()
                if (placement != solution) found = placement
                return
            }
            for (c in 0 until n) {
                if (usedCol[c]) continue
                val region = regions[r][c]
                if (usedRegion[region]) continue
                if (!ok(r, c)) continue
                usedCol[c] = true; usedRegion[region] = true; col[r] = c
                recurse(r + 1)
                usedCol[c] = false; usedRegion[region] = false; col[r] = -1
                if (found != null) return
            }
        }

        recurse(0)
        return found
    }

    /** True when every cell tagged [region] forms one 4-connected blob. */
    private fun regionContiguous(regions: Array<IntArray>, region: Int, n: Int): Boolean {
        var total = 0
        var start: GridPos? = null
        for (r in 0 until n) {
            for (c in 0 until n) {
                if (regions[r][c] == region) {
                    total += 1
                    if (start == null) start = GridPos(r, c)
                }
            }
        }
        val seed = start ?: return false

        val seen = hashSetOf(seed)
        val stack = ArrayDeque(listOf(seed))
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            for ((dr, dc) in DIRS) {
                val nr = p.row + dr
                val nc = p.col + dc
                if (nr < 0 || nc < 0 || nr >= n || nc >= n) continue
                val np = GridPos(nr, nc)
                if (regions[nr][nc] == region && np !in seen) {
                    seen.add(np); stack.addLast(np)
                }
            }
        }
        return seen.size == total
    }

    // region Daily seeding

    private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** `"yyyy-MM-dd|size"` for the device's local calendar day. */
    fun dailyKey(date: LocalDate, size: Int): String = DAY_FORMAT.format(date) + "|$size"

    fun dailySeed(date: LocalDate, size: Int): ULong {
        var hash = 0xcbf2_9ce4_8422_2325uL // FNV-1a
        for (byte in dailyKey(date, size).encodeToByteArray()) {
            hash = hash xor byte.toUByte().toULong()
            hash *= 0x0000_0100_0000_01b3uL
        }
        return hash
    }

    // endregion

    // region Royal Guard placement

    fun pickGuard(puzzle: Puzzle, seed: ULong): GridPos {
        val rng = SeededRng(seed xor 0x1234_ABCD_5678_9EF0uL)
        repeat(400) {
            val r = rng.nextInt(puzzle.size)
            val c = rng.nextInt(puzzle.size)
            if (puzzle.solution[r] != c) return GridPos(r, c)
        }
        return GridPos(0, (puzzle.solution[0] + 2) % puzzle.size)
    }

    // endregion

    // region Solution counter (also used by tests)

    /** Counts full solutions, stopping once [cap] is reached. */
    fun countSolutions(size: Int, regions: List<List<Int>>, rules: RuleSet, cap: Int): Int {
        val n = size
        val col = IntArray(n) { -1 }
        val usedCol = BooleanArray(n)
        val usedRegion = BooleanArray(n)
        var count = 0

        fun ok(r: Int, c: Int): Boolean {
            if (r > 0 && col[r - 1] >= 0 && abs(col[r - 1] - c) <= 1) return false
            if (rules.diagonalTwo && r > 1 && col[r - 2] >= 0 && abs(col[r - 2] - c) == 2) return false
            return true
        }

        fun recurse(r: Int) {
            if (count >= cap) return
            if (r == n) { count += 1; return }
            for (c in 0 until n) {
                if (usedCol[c]) continue
                val region = regions[r][c]
                if (usedRegion[region]) continue
                if (!ok(r, c)) continue
                usedCol[c] = true; usedRegion[region] = true; col[r] = c
                recurse(r + 1)
                usedCol[c] = false; usedRegion[region] = false; col[r] = -1
                if (count >= cap) return
            }
        }

        recurse(0)
        return count
    }

    // endregion

    // region Steps

    private fun buildSolution(n: Int, rules: RuleSet, rng: SeededRng): List<Int>? {
        val col = IntArray(n) { -1 }
        val used = BooleanArray(n)

        fun ok(r: Int, c: Int): Boolean {
            if (r > 0 && abs(col[r - 1] - c) <= 1) return false
            if (rules.diagonalTwo && r > 1 && abs(col[r - 2] - c) == 2) return false
            return true
        }

        fun recurse(r: Int): Boolean {
            if (r == n) return true
            for (c in (0 until n).shuffledSwift(rng)) {
                if (used[c] || !ok(r, c)) continue
                col[r] = c; used[c] = true
                if (recurse(r + 1)) return true
                used[c] = false; col[r] = -1
            }
            return false
        }

        return if (recurse(0)) col.toList() else null
    }

    private fun growRegions(n: Int, solution: List<Int>, rng: SeededRng): Array<IntArray> {
        val regions = Array(n) { IntArray(n) { -1 } }
        val size = IntArray(n) { 1 }
        val candidates = Array(n) { mutableListOf<GridPos>() }

        fun inBounds(r: Int, c: Int) = r >= 0 && c >= 0 && r < n && c < n
        fun addCandidates(k: Int, r: Int, c: Int) {
            for ((dr, dc) in DIRS) {
                val nr = r + dr
                val nc = c + dc
                if (inBounds(nr, nc) && regions[nr][nc] == -1) {
                    candidates[k].add(GridPos(nr, nc))
                }
            }
        }

        for (r in 0 until n) {
            regions[r][solution[r]] = r
            addCandidates(r, r, solution[r])
        }

        var remaining = n * n - n
        var safety = 0
        while (remaining > 0 && safety < n * n * 20) {
            safety += 1
            var pool = (0 until n).filter { candidates[it].isNotEmpty() }
            if (pool.isEmpty()) break
            pool = pool.sortedBy { size[it] } // stable, like Swift's sort
            pool = pool.take(max(1, ceil(pool.size * 0.6).toInt()))
            val k = pool[rng.nextInt(pool.size)]

            var picked: GridPos? = null
            while (candidates[k].isNotEmpty()) {
                val list = candidates[k]
                val i = rng.nextInt(list.size)
                val cell = list[i]
                java.util.Collections.swap(list, i, list.size - 1)
                list.removeAt(list.size - 1)
                if (regions[cell.row][cell.col] == -1) { picked = cell; break }
            }
            val cell = picked ?: continue
            regions[cell.row][cell.col] = k
            size[k] += 1
            remaining -= 1
            addCandidates(k, cell.row, cell.col)
        }

        // Attach any orphan cells to a neighbouring realm.
        for (r in 0 until n) {
            for (c in 0 until n) {
                if (regions[r][c] != -1) continue
                var attached = false
                for ((dr, dc) in DIRS) {
                    val nr = r + dr
                    val nc = c + dc
                    if (inBounds(nr, nc) && regions[nr][nc] >= 0) {
                        regions[r][c] = regions[nr][nc]; attached = true; break
                    }
                }
                if (!attached) regions[r][c] = 0
            }
        }
        return regions
    }

    private fun colorMap(n: Int, rng: SeededRng): List<Int> =
        (0 until 9).shuffledSwift(rng).take(n)

    // endregion
}
