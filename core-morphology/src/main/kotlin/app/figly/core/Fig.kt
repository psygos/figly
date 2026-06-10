package app.figly.core

/** What a cell of the fig is. */
enum class CellType { WOOD, LEAF, THORN, DRUPE, SCAR }

/**
 * One cell of a grown fig.
 * [day] is 0 for the seed, 1..7 for Monday..Sunday.
 * Cells are kept in placement order — the order growth is witnessed.
 */
data class FigCell(
    val pos: Pos,
    val type: CellType,
    val day: Int,
)

/** Where a fig's tip rests, and everything it has grown. */
data class Fig(
    /** In placement order. The first cell is always the seed (wood, day 0). */
    val cells: List<FigCell>,
    val tip: Pos,
    /** Number of day slots consumed (sealed or missed), 0..7. */
    val daysGrown: Int,
    val stats: FigStats,
) {
    fun cellAt(pos: Pos): FigCell? = cells.firstOrNull { it.pos == pos }
    fun cellsOfDay(day: Int): List<FigCell> = cells.filter { it.day == day }
}

data class FigStats(
    val cells: Int,
    val wood: Int,
    val leaves: Int,
    val thorns: Int,
    val drupes: Int,
    val scars: Int,
    /** Highest row reached (row 0 = top of the matrix). */
    val heightRow: Int,
    /** Mean mood over sealed days, null if every day was missed. */
    val meanMood: Double?,
    val season: Season,
)

/**
 * A pressed week's climate, from its mean day-rating. Five equal bands on
 * the 1–5 scale. A climate, never a verdict — bad weeks aren't red;
 * ashfall is rust-beautiful. A fully-missed week is barren and carries
 * no tint: color belongs to lived weeks.
 */
enum class Season(val hex: String) {
    ASHFALL("#A3653F"),
    OVERCAST("#7C8B94"),
    TEMPERATE("#8FA381"),
    VERDANT("#5FA98B"),
    AURIC("#D6B36A"),
    BARREN("#3A3933");

    companion object {
        fun fromMeanMood(mean: Double?): Season = when {
            mean == null -> BARREN
            mean < 1.8 -> ASHFALL
            mean < 2.6 -> OVERCAST
            mean < 3.4 -> TEMPERATE
            mean < 4.2 -> VERDANT
            else -> AURIC
        }
    }
}
