//
//  PuzzleGenerator.swift
//  QueensGame
//
//  Builds an N×N Queens puzzle with contiguous colour regions and a
//  *verified unique* solution, driven by a seeded RNG so "Daily" puzzles
//  are reproducible.
//
//  Pipeline:
//    1. Backtrack a Queen placement (one per row & column, none touching).
//    2. Flood-grow one colour region from each Queen until the board is full.
//    3. Count solutions under the active rules — keep only when exactly one.
//

import Foundation

/// SplitMix64 — a tiny deterministic `RandomNumberGenerator`.
struct SeededRNG: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed == 0 ? 0x9E37_79B9_7F4A_7C15 : seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

enum PuzzleGenerator {

    // MARK: - Public entry

    static func generate(size n: Int, rules: RuleSet, seed: UInt64) -> Puzzle {
        var rng = SeededRNG(seed: seed)
        var lastRegions: [[Int]] = []
        var lastSolution: [Int] = Array(0..<n)

        for _ in 0..<30 {
            guard let solution = buildSolution(size: n, rules: rules, rng: &rng) else { continue }
            for _ in 0..<12 {
                var regions = growRegions(size: n, solution: solution, rng: &rng)
                lastRegions = regions
                lastSolution = solution
                if repairToUnique(&regions, solution: solution, rules: rules, rng: &rng) {
                    return Puzzle(size: n,
                                  regions: regions,
                                  solution: solution,
                                  ruleSet: rules,
                                  colorMap: colorMap(size: n, rng: &rng),
                                  decree: nil,
                                  guardPos: nil)
                }
            }
        }

        // Extremely unlikely fallback: ship the best board we managed to build.
        return Puzzle(size: n,
                      regions: lastRegions.isEmpty ? growRegions(size: n, solution: lastSolution, rng: &rng) : lastRegions,
                      solution: lastSolution,
                      ruleSet: rules,
                      colorMap: colorMap(size: n, rng: &rng),
                      decree: nil,
                      guardPos: nil)
    }

    /// Reshapes `regions` (contiguity preserved) until the puzzle's only
    /// solution is `solution`. Returns `false` if it gets stuck.
    private static func repairToUnique(_ regions: inout [[Int]],
                                       solution: [Int],
                                       rules: RuleSet,
                                       rng: inout SeededRNG) -> Bool {
        let n = solution.count
        let dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]

        for _ in 0..<300 {
            guard let alt = firstAlternate(size: n, regions: regions, rules: rules, avoiding: solution) else {
                return true
            }

            var diffRows = (0..<n).filter { alt[$0] != solution[$0] }
            diffRows.shuffle(using: &rng)

            var moved = false
            rowLoop: for r in diffRows {
                let vr = r, vc = alt[r]
                let from = regions[vr][vc]

                var targets: [Int] = []
                for (dr, dc) in dirs {
                    let nr = vr + dr, nc = vc + dc
                    guard nr >= 0, nc >= 0, nr < n, nc < n else { continue }
                    let other = regions[nr][nc]
                    if other != from { targets.append(other) }
                }
                targets.shuffle(using: &rng)

                for to in targets {
                    regions[vr][vc] = to
                    if regionContiguous(regions, region: from, size: n) {
                        moved = true
                        break rowLoop
                    }
                    regions[vr][vc] = from        // revert, try next
                }
            }

            if !moved { return false }
        }
        return firstAlternate(size: n, regions: regions, rules: rules, avoiding: solution) == nil
    }

    /// First complete placement that isn't `solution`, or `nil` if none exists.
    private static func firstAlternate(size n: Int,
                                       regions: [[Int]],
                                       rules: RuleSet,
                                       avoiding solution: [Int]) -> [Int]? {
        var col = Array(repeating: -1, count: n)
        var usedCol = Array(repeating: false, count: n)
        var usedRegion = Array(repeating: false, count: n)
        var found: [Int]?

        func ok(_ r: Int, _ c: Int) -> Bool {
            if r > 0, col[r - 1] >= 0, abs(col[r - 1] - c) <= 1 { return false }
            if rules.diagonalTwo, r > 1, col[r - 2] >= 0, abs(col[r - 2] - c) == 2 { return false }
            return true
        }

        func recurse(_ r: Int) {
            if found != nil { return }
            if r == n { if col != solution { found = col }; return }
            for c in 0..<n {
                if usedCol[c] { continue }
                let region = regions[r][c]
                if usedRegion[region] { continue }
                if !ok(r, c) { continue }
                usedCol[c] = true; usedRegion[region] = true; col[r] = c
                recurse(r + 1)
                usedCol[c] = false; usedRegion[region] = false; col[r] = -1
                if found != nil { return }
            }
        }

        recurse(0)
        return found
    }

    /// True when every cell tagged `region` forms one 4-connected blob.
    private static func regionContiguous(_ regions: [[Int]], region: Int, size n: Int) -> Bool {
        var total = 0
        var start: GridPos?
        for r in 0..<n {
            for c in 0..<n where regions[r][c] == region {
                total += 1
                if start == nil { start = GridPos(row: r, col: c) }
            }
        }
        guard let seed = start else { return false }

        var seen: Set<GridPos> = [seed]
        var stack = [seed]
        while let p = stack.popLast() {
            for (dr, dc) in [(1, 0), (-1, 0), (0, 1), (0, -1)] {
                let nr = p.row + dr, nc = p.col + dc
                guard nr >= 0, nc >= 0, nr < n, nc < n else { continue }
                let np = GridPos(row: nr, col: nc)
                if regions[nr][nc] == region, !seen.contains(np) {
                    seen.insert(np); stack.append(np)
                }
            }
        }
        return seen.count == total
    }

    // MARK: - Daily seeding

    static func dailyKey(date: Date, size: Int) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.calendar = Calendar(identifier: .gregorian)
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date) + "|\(size)"
    }

    static func dailySeed(date: Date, size: Int) -> UInt64 {
        var hash: UInt64 = 0xcbf2_9ce4_8422_2325           // FNV-1a
        for byte in dailyKey(date: date, size: size).utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 0x0000_0100_0000_01b3
        }
        return hash
    }

    // MARK: - Royal Guard placement

    static func pickGuard(for puzzle: Puzzle, seed: UInt64) -> GridPos {
        var rng = SeededRNG(seed: seed ^ 0x1234_ABCD_5678_9EF0)
        for _ in 0..<400 {
            let r = Int.random(in: 0..<puzzle.size, using: &rng)
            let c = Int.random(in: 0..<puzzle.size, using: &rng)
            if puzzle.solution[r] != c { return GridPos(row: r, col: c) }
        }
        return GridPos(row: 0, col: (puzzle.solution[0] + 2) % puzzle.size)
    }

    // MARK: - Solution counter (also used by tests)

    /// Counts full solutions, stopping once `cap` is reached.
    static func countSolutions(size n: Int, regions: [[Int]], rules: RuleSet, cap: Int) -> Int {
        var col = Array(repeating: -1, count: n)
        var usedCol = Array(repeating: false, count: n)
        var usedRegion = Array(repeating: false, count: n)
        var count = 0

        func ok(_ r: Int, _ c: Int) -> Bool {
            if r > 0, col[r - 1] >= 0, abs(col[r - 1] - c) <= 1 { return false }
            if rules.diagonalTwo, r > 1, col[r - 2] >= 0, abs(col[r - 2] - c) == 2 { return false }
            return true
        }

        func recurse(_ r: Int) {
            if count >= cap { return }
            if r == n { count += 1; return }
            for c in 0..<n {
                if usedCol[c] { continue }
                let region = regions[r][c]
                if usedRegion[region] { continue }
                if !ok(r, c) { continue }
                usedCol[c] = true; usedRegion[region] = true; col[r] = c
                recurse(r + 1)
                usedCol[c] = false; usedRegion[region] = false; col[r] = -1
                if count >= cap { return }
            }
        }

        recurse(0)
        return count
    }

    // MARK: - Steps

    private static func buildSolution(size n: Int, rules: RuleSet, rng: inout SeededRNG) -> [Int]? {
        var col = Array(repeating: -1, count: n)
        var used = Array(repeating: false, count: n)

        func ok(_ r: Int, _ c: Int) -> Bool {
            if r > 0, abs(col[r - 1] - c) <= 1 { return false }
            if rules.diagonalTwo, r > 1, abs(col[r - 2] - c) == 2 { return false }
            return true
        }

        func recurse(_ r: Int) -> Bool {
            if r == n { return true }
            for c in (0..<n).shuffled(using: &rng) {
                if used[c] || !ok(r, c) { continue }
                col[r] = c; used[c] = true
                if recurse(r + 1) { return true }
                used[c] = false; col[r] = -1
            }
            return false
        }

        return recurse(0) ? col : nil
    }

    private static func growRegions(size n: Int, solution: [Int], rng: inout SeededRNG) -> [[Int]] {
        var regions = Array(repeating: Array(repeating: -1, count: n), count: n)
        var size = Array(repeating: 1, count: n)
        var candidates = Array(repeating: [GridPos](), count: n)
        let dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]

        func inBounds(_ r: Int, _ c: Int) -> Bool { r >= 0 && c >= 0 && r < n && c < n }
        func addCandidates(_ k: Int, _ r: Int, _ c: Int) {
            for (dr, dc) in dirs {
                let nr = r + dr, nc = c + dc
                if inBounds(nr, nc), regions[nr][nc] == -1 {
                    candidates[k].append(GridPos(row: nr, col: nc))
                }
            }
        }

        for r in 0..<n {
            regions[r][solution[r]] = r
            addCandidates(r, r, solution[r])
        }

        var remaining = n * n - n
        var safety = 0
        while remaining > 0, safety < n * n * 20 {
            safety += 1
            var pool = (0..<n).filter { !candidates[$0].isEmpty }
            if pool.isEmpty { break }
            pool.sort { size[$0] < size[$1] }
            pool = Array(pool.prefix(max(1, Int(ceil(Double(pool.count) * 0.6)))))
            let k = pool[Int.random(in: 0..<pool.count, using: &rng)]

            var picked: GridPos?
            while !candidates[k].isEmpty {
                let i = Int.random(in: 0..<candidates[k].count, using: &rng)
                let cell = candidates[k][i]
                candidates[k].swapAt(i, candidates[k].count - 1)
                candidates[k].removeLast()
                if regions[cell.row][cell.col] == -1 { picked = cell; break }
            }
            guard let cell = picked else { continue }
            regions[cell.row][cell.col] = k
            size[k] += 1
            remaining -= 1
            addCandidates(k, cell.row, cell.col)
        }

        // Attach any orphan cells to a neighbouring realm.
        for r in 0..<n {
            for c in 0..<n where regions[r][c] == -1 {
                var attached = false
                for (dr, dc) in dirs {
                    let nr = r + dr, nc = c + dc
                    if inBounds(nr, nc), regions[nr][nc] >= 0 {
                        regions[r][c] = regions[nr][nc]; attached = true; break
                    }
                }
                if !attached { regions[r][c] = 0 }
            }
        }
        return regions
    }

    private static func colorMap(size n: Int, rng: inout SeededRNG) -> [Int] {
        Array(Array(0..<9).shuffled(using: &rng).prefix(n))
    }
}
