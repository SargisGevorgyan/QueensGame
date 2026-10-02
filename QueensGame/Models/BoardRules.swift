//
//  BoardRules.swift
//  QueensGame
//
//  Pure functions that read the board + puzzle and report conflicts,
//  completion and the "auto-X" overlay. No SwiftUI, fully testable.
//

import Foundation

enum BoardRules {

    /// Validate the current board against the puzzle's rules.
    static func analyse(cells: [[Cell]], puzzle: Puzzle) -> BoardAnalysis {
        let n = puzzle.size
        var result = BoardAnalysis(size: n)

        var queens: [GridPos] = []
        var byRow = Array(repeating: 0, count: n)
        var byCol = Array(repeating: 0, count: n)
        var byRegion = Array(repeating: 0, count: n)

        for r in 0..<n {
            for c in 0..<n where cells[r][c].base == .queen {
                let pos = GridPos(row: r, col: c)
                queens.append(pos)
                byRow[r] += 1
                byCol[c] += 1
                byRegion[puzzle.regions[r][c]] += 1
            }
        }

        var conflicts: Set<GridPos> = []

        // Pairwise adjacency (and the Assassin's Range diagonal-two clash).
        for i in 0..<queens.count {
            for j in (i + 1)..<queens.count {
                let a = queens[i], b = queens[j]
                let dr = abs(a.row - b.row), dc = abs(a.col - b.col)
                var clash = max(dr, dc) == 1
                if puzzle.ruleSet.diagonalTwo, dr == 2, dc == 2 { clash = true }
                if clash { conflicts.insert(a); conflicts.insert(b) }
            }
        }

        // Duplicates in a rank / file / realm.
        for q in queens {
            if byRow[q.row] > 1 || byCol[q.col] > 1 || byRegion[puzzle.regions[q.row][q.col]] > 1 {
                conflicts.insert(q)
            }
        }

        for q in queens where !conflicts.contains(q) {
            if byRow[q.row] == 1 { result.rowDone[q.row] = true }
            if byCol[q.col] == 1 { result.colDone[q.col] = true }
            let region = puzzle.regions[q.row][q.col]
            if byRegion[region] == 1 { result.regionDone[region] = true }
        }

        result.queens = queens
        result.conflicts = conflicts
        result.solved = queens.count == n && conflicts.isEmpty
        return result
    }

    /// Every square a Queen at `queen` should auto-mark with an X:
    /// the 8 adjacent squares, the whole rank & file, and the whole colour
    /// realm. (The caller only writes into squares that are currently empty;
    /// duplicates in this list are harmless.)
    static func autoXTargets(for queen: GridPos, puzzle: Puzzle) -> [GridPos] {
        let n = puzzle.size
        var targets: [GridPos] = []

        // 8 adjacent
        for dr in -1...1 {
            for dc in -1...1 where !(dr == 0 && dc == 0) {
                let r = queen.row + dr, c = queen.col + dc
                if r >= 0, c >= 0, r < n, c < n { targets.append(GridPos(row: r, col: c)) }
            }
        }
        // whole rank & file
        for i in 0..<n {
            targets.append(GridPos(row: queen.row, col: i))
            targets.append(GridPos(row: i, col: queen.col))
        }
        // whole colour realm
        let region = puzzle.regions[queen.row][queen.col]
        for r in 0..<n {
            for c in 0..<n where puzzle.regions[r][c] == region {
                targets.append(GridPos(row: r, col: c))
            }
        }
        return targets
    }

    /// Just the colour realm of `queen` — used by the Color Lock decree when
    /// the Auto-X master switch is off.
    static func realmTargets(for queen: GridPos, puzzle: Puzzle) -> [GridPos] {
        let n = puzzle.size
        let region = puzzle.regions[queen.row][queen.col]
        var targets: [GridPos] = []
        for r in 0..<n {
            for c in 0..<n where puzzle.regions[r][c] == region {
                targets.append(GridPos(row: r, col: c))
            }
        }
        return targets
    }
}
