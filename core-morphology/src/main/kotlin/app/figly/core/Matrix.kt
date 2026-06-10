package app.figly.core

/**
 * The Glyph Matrix of the Phone (4a) Pro: 13×13 logical, 137 physical LEDs
 * in a circular arrangement (corners absent). Row 0 is the top.
 *
 * The logical length is always fetched from the SDK at runtime on device
 * (Common.getDeviceMatrixLength()); this constant is the morphology's truth
 * and is asserted against the device value at the glyph boundary.
 */
object Matrix {
    const val SIZE = 13

    /** Valid column ranges per row — the circular mask. */
    private val MASK: Array<IntRange> = arrayOf(
        4..8,   // row 0
        2..10,  // row 1
        1..11,  // row 2
        1..11,  // row 3
        0..12,  // row 4
        0..12,  // row 5
        0..12,  // row 6
        0..12,  // row 7
        0..12,  // row 8
        1..11,  // row 9
        1..11,  // row 10
        2..10,  // row 11
        4..8,   // row 12
    )

    /** Soil is static: row 11, cols 3..9. Not part of the fig. */
    const val SOIL_ROW = 11
    val SOIL_COLS = 3..9

    /** The seed is planted here on Monday 00:00 local. */
    const val SEED_ROW = 10
    const val SEED_COL = 6

    fun inMask(row: Int, col: Int): Boolean =
        row in 0 until SIZE && col in MASK[row]

    fun isSoil(row: Int, col: Int): Boolean =
        row == SOIL_ROW && col in SOIL_COLS

    fun maskColumns(row: Int): IntRange = MASK[row]

    /** All 137 valid positions, row-major. */
    val positions: List<Pos> by lazy {
        buildList {
            for (r in 0 until SIZE) for (c in MASK[r]) add(Pos(r, c))
        }
    }
}

/** A position on the matrix. row 0 = top. */
data class Pos(val row: Int, val col: Int) {
    val key: Int get() = row * Matrix.SIZE + col
}
