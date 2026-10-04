//
//  GameModels.swift
//  QueensGame
//
//  Value types describing a single Queens puzzle and its live state.
//

import Foundation

/// A row/column coordinate on the board. Also serves as a Queen's identity
/// for Smart Auto-X ownership (a Queen is uniquely identified by its square).
struct GridPos: Hashable {
    let row: Int
    let col: Int
}

/// The player-owned state of a square. Auto-generated X marks are layered on
/// top via `Cell.autoOwners` and never overwrite a `manualX`.
///
/// `base == .manualX`  ⇔  the spec's `manuallyMarked == true`.
enum CellBase: Equatable {
    case empty
    case manualX   // an X the player placed (or promoted) — never auto-removed
    case queen
}

/// What a square should render.
enum CellDisplay: Equatable {
    case empty
    case x
    case queen
}

/// One board square: a base state plus the set of Queens that have
/// auto-filled an X here.
struct Cell: Equatable {
    var base: CellBase = .empty
    /// The spec's `generatedByQueens` — QueenIDs (their `GridPos`) that
    /// auto-marked an X on this square.
    var autoOwners: Set<GridPos> = []

    /// True when the X shown here is purely auto-generated (no manual mark).
    var isAutoX: Bool { base == .empty && !autoOwners.isEmpty }

    var display: CellDisplay {
        switch base {
        case .queen:   return .queen
        case .manualX: return .x
        case .empty:   return autoOwners.isEmpty ? .empty : .x
        }
    }
}

/// One of the four sides of a square — used to draw realm walls.
enum WallEdge: Hashable {
    case top, bottom, leading, trailing
}

/// Standard puzzle, or the rule-bending "Royal Decrees" variant.
enum GameMode: String, CaseIterable, Identifiable {
    case standard
    case decrees
    var id: String { rawValue }
    var title: String { self == .standard ? "Standard" : "Royal Decrees" }
    /// Royal Decrees is part of the one-time Royal Pass unlock.
    var requiresPremium: Bool { self == .decrees }
}

/// Light / dark / follow-system.
enum AppTheme: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
}

/// Rule toggles that both the generator and the validator honour.
struct RuleSet: Equatable {
    /// Assassin's Range: two Queens also clash when exactly two apart on a diagonal.
    var diagonalTwo: Bool = false
}

/// A generated puzzle: fixed for the life of one round.
struct Puzzle {
    let size: Int
    /// `regions[row][col]` → realm index in `0..<size`.
    let regions: [[Int]]
    /// `solution[row]` → the column holding that row's Queen in the unique solution.
    let solution: [Int]
    let ruleSet: RuleSet
    /// `colorMap[realm]` → palette slot in `0..<9`.
    let colorMap: [Int]
    var decree: Decree?
    var guardPos: GridPos?
}

/// A "Royal Decree" — a per-round modifier proclaimed by the herald.
enum Decree: String, CaseIterable, Identifiable {
    case fogOfWar      = "fog"
    case royalGuard    = "guard"
    case assassinsRange = "assassin"
    case colorLock     = "lock"
    case truce         = "truce"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .fogOfWar:       return "The Fog of War"
        case .royalGuard:     return "The Royal Guard"
        case .assassinsRange: return "Assassin's Range"
        case .colorLock:      return "Color Lock"
        case .truce:          return "Royal Truce"
        }
    }

    var blurb: String {
        switch self {
        case .fogOfWar:       return "Realms stay hidden until you touch a square inside them."
        case .royalGuard:     return "A fixed Guard holds its square like a permanent ×."
        case .assassinsRange: return "Queens also strike two squares away along a diagonal."
        case .colorLock:      return "Placing a Queen auto-marks × on the rest of its realm."
        case .truce:          return "No decree in force — the herald will return."
        }
    }

    var systemImage: String {
        switch self {
        case .fogOfWar:       return "cloud.fog.fill"
        case .royalGuard:     return "shield.lefthalf.filled"
        case .assassinsRange: return "scope"
        case .colorLock:      return "lock.fill"
        case .truce:          return "flag.slash"
        }
    }
}

/// The outcome of validating the board against the puzzle's rules.
struct BoardAnalysis {
    var queens: [GridPos] = []
    var conflicts: Set<GridPos> = []
    var rowDone: [Bool]
    var colDone: [Bool]
    var regionDone: [Bool]
    var solved: Bool = false

    init(size: Int) {
        rowDone = Array(repeating: false, count: size)
        colDone = Array(repeating: false, count: size)
        regionDone = Array(repeating: false, count: size)
    }
}

/// Roman numeral for realm labels (1...9).
func roman(_ value: Int) -> String {
    let table: [(Int, String)] = [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]
    var n = value
    var out = ""
    for (v, s) in table { while n >= v { out += s; n -= v } }
    return out
}
