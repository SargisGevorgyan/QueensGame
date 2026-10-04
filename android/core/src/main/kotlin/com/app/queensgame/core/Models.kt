//
//  Models.kt
//  QueensGame (Android)
//
//  Value types describing a single Queens puzzle and its live state.
//  A straight port of the iOS `GameModels.swift`.
//

package com.app.queensgame.core

/**
 * A row/column coordinate on the board. Also serves as a Queen's identity
 * for Smart Auto-X ownership (a Queen is uniquely identified by its square).
 */
data class GridPos(val row: Int, val col: Int)

/**
 * The player-owned state of a square. Auto-generated X marks are layered on
 * top via [Cell.autoOwners] and never overwrite a [MANUAL_X].
 */
enum class CellBase {
    EMPTY,
    /** An X the player placed (or promoted) — never auto-removed. */
    MANUAL_X,
    QUEEN,
}

/** What a square should render. */
enum class CellDisplay { EMPTY, X, QUEEN }

/**
 * One board square: a base state plus the set of Queens that have
 * auto-filled an X here.
 */
data class Cell(
    val base: CellBase = CellBase.EMPTY,
    val autoOwners: Set<GridPos> = emptySet(),
) {
    /** True when the X shown here is purely auto-generated (no manual mark). */
    val isAutoX: Boolean get() = base == CellBase.EMPTY && autoOwners.isNotEmpty()

    val display: CellDisplay
        get() = when (base) {
            CellBase.QUEEN -> CellDisplay.QUEEN
            CellBase.MANUAL_X -> CellDisplay.X
            CellBase.EMPTY -> if (autoOwners.isEmpty()) CellDisplay.EMPTY else CellDisplay.X
        }
}

typealias Board = List<List<Cell>>

fun emptyBoard(n: Int): Board = List(n) { List(n) { Cell() } }

/** Returns a copy of the board with the cell at [pos] replaced. */
fun Board.update(pos: GridPos, transform: (Cell) -> Cell): Board =
    mapIndexed { r, row ->
        if (r != pos.row) row else row.mapIndexed { c, cell -> if (c == pos.col) transform(cell) else cell }
    }

operator fun Board.get(pos: GridPos): Cell = this[pos.row][pos.col]

/** One of the four sides of a square — used to draw realm walls. */
enum class WallEdge { TOP, BOTTOM, LEADING, TRAILING }

/** Standard puzzle, or the rule-bending "Royal Decrees" variant. */
enum class GameMode(val key: String, val title: String) {
    STANDARD("standard", "Standard"),
    DECREES("decrees", "Royal Decrees");

    /** Royal Decrees is unlocked by the one-time Royal Pass purchase. */
    val requiresPremium: Boolean get() = this == DECREES

    companion object {
        fun fromKey(key: String?): GameMode? = entries.firstOrNull { it.key == key }
    }
}

/** Light / dark / follow-system. */
enum class AppTheme(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(key: String?): AppTheme? = entries.firstOrNull { it.key == key }
    }
}

/** Rule toggles that both the generator and the validator honour. */
data class RuleSet(
    /** Assassin's Range: two Queens also clash when exactly two apart on a diagonal. */
    val diagonalTwo: Boolean = false,
)

/** A generated puzzle: fixed for the life of one round. */
data class Puzzle(
    val size: Int,
    /** `regions[row][col]` → realm index in `0 until size`. */
    val regions: List<List<Int>>,
    /** `solution[row]` → the column holding that row's Queen in the unique solution. */
    val solution: List<Int>,
    val ruleSet: RuleSet,
    /** `colorMap[realm]` → palette slot in `0 until 9`. */
    val colorMap: List<Int>,
    val decree: Decree? = null,
    val guardPos: GridPos? = null,
) {
    fun region(pos: GridPos): Int = regions[pos.row][pos.col]
}

/** A "Royal Decree" — a per-round modifier proclaimed by the herald. */
enum class Decree(val key: String, val title: String, val blurb: String) {
    FOG_OF_WAR("fog", "The Fog of War", "Realms stay hidden until you touch a square inside them."),
    ROYAL_GUARD("guard", "The Royal Guard", "A fixed Guard holds its square like a permanent ×."),
    ASSASSINS_RANGE("assassin", "Assassin's Range", "Queens also strike two squares away along a diagonal."),
    COLOR_LOCK("lock", "Color Lock", "Placing a Queen auto-marks × on the rest of its realm."),
    TRUCE("truce", "Royal Truce", "No decree in force — the herald will return."),
}

/** The outcome of validating the board against the puzzle's rules. */
data class BoardAnalysis(
    val queens: List<GridPos>,
    val conflicts: Set<GridPos>,
    val rowDone: List<Boolean>,
    val colDone: List<Boolean>,
    val regionDone: List<Boolean>,
    val solved: Boolean,
)

/** Roman numeral for realm labels (1...9). */
fun roman(value: Int): String {
    val table = listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    var n = value
    val out = StringBuilder()
    for ((v, s) in table) {
        while (n >= v) { out.append(s); n -= v }
    }
    return out.toString()
}
