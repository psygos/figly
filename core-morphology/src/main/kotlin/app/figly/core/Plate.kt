package app.figly.core

/**
 * Plate geometry — the pressing treatment, in the reference plate's own
 * coordinate space (760 × 1040). Every renderer (Compose canvas, SVG
 * export, thumbnails) draws from this one model, so a specimen presses
 * the same way everywhere, forever.
 *
 * Ink is ink: no glow, blur, gradient, or shadow ever touches a plate.
 */
object Plate {
    const val WIDTH = 760.0
    const val HEIGHT = 1040.0

    // Plate surface.
    const val SURFACE_X = 28.0
    const val SURFACE_Y = 28.0
    const val SURFACE_W = 704.0
    const val SURFACE_H = 984.0

    // Dot field: grid origin and pitch.
    const val GRID_X = 116.0
    const val GRID_Y = 96.0
    const val PITCH = 44.0

    // Matte ink dot radii.
    const val R_PAPER = 3.0
    const val R_SOIL = 5.5
    const val R_WOOD = 9.5
    const val R_LEAF = 8.0
    const val R_THORN = 6.0
    const val R_SCAR = 8.0
    const val R_DRUPE = 10.0

    enum class Ink { PAPER, SOIL, WOOD, LEAF, THORN, SCAR, DRUPE }

    data class Dot(
        val x: Double,
        val y: Double,
        val r: Double,
        val ink: Ink,
        val alpha: Double,
    )

    /**
     * All dots of a pressed plate: faint dot paper at unlit positions,
     * the soil band, and the fig's cells as matte ink — radius jittered
     * ±7 %, center ±0.6 px, seeded per cell so the same plate always
     * presses the same way.
     */
    fun dots(fig: Fig, seed: UInt): List<Dot> {
        val lit = fig.cells.associateBy { it.pos.key }
        val out = ArrayList<Dot>(160)

        for (p in Matrix.positions) {
            val cx = GRID_X + p.col * PITCH
            val cy = GRID_Y + p.row * PITCH
            val cell = lit[p.key]
            if (cell != null) {
                val j = Mulberry32(fnv1a32("press:$seed:${p.row}:${p.col}"))
                val r = baseRadius(cell.type) * (1.0 + (j.next() * 2 - 1) * 0.07)
                val dx = (j.next() * 2 - 1) * 0.6
                val dy = (j.next() * 2 - 1) * 0.6
                out += Dot(cx + dx, cy + dy, r, ink(cell.type), alpha(cell.type))
            } else if (Matrix.isSoil(p.row, p.col)) {
                val j = Mulberry32(fnv1a32("soil:$seed:${p.col}"))
                val r = R_SOIL * (1.0 + (j.next() * 2 - 1) * 0.06)
                val dx = (j.next() * 2 - 1) * 0.5
                val dy = (j.next() * 2 - 1) * 0.5
                out += Dot(cx + dx, cy + dy, r, Ink.SOIL, 0.9)
            } else {
                out += Dot(cx, cy, R_PAPER, Ink.PAPER, 0.45)
            }
        }
        return out
    }

    private fun baseRadius(t: CellType): Double = when (t) {
        CellType.WOOD -> R_WOOD
        CellType.LEAF -> R_LEAF
        CellType.THORN -> R_THORN
        CellType.SCAR -> R_SCAR
        CellType.DRUPE -> R_DRUPE
    }

    private fun ink(t: CellType): Ink = when (t) {
        CellType.WOOD -> Ink.WOOD
        CellType.LEAF -> Ink.LEAF
        CellType.THORN -> Ink.THORN
        CellType.SCAR -> Ink.SCAR
        CellType.DRUPE -> Ink.DRUPE
    }

    private fun alpha(t: CellType): Double = when (t) {
        CellType.LEAF -> 0.8
        else -> 1.0
    }

    // Label block.
    const val LABEL_X = 88.0
    const val ID_BASELINE = 752.0
    const val UNDERLINE_Y = 764.0
    const val UNDERLINE_W = 236.0
    const val UNDERLINE_H = 2.0
    const val DATES_BASELINE = 804.0
    const val TICK_X = 88.0
    const val TICK_Y = 824.0
    const val TICK_W = 18.0
    const val TICK_H = 3.0
    const val SEASON_X = 116.0
    const val SEASON_BASELINE = 832.0
    const val COUNTS_BASELINE = 862.0
    const val FOOTER_X = 672.0
    const val FOOTER_BASELINE = 986.0
}

/** Everything written on a plate's label block. */
data class PlateLabel(
    val figId: String,        // FIG-2026-W24
    val dateRange: String,    // 08–14 JUN 2026
    val collected: String?,   // DELHI, or null → "—"
    val seasonName: String,   // TEMPERATE
    val seasonHex: String,
    val stats: FigStats,
    val plateNumber: Int,
) {
    val collectedLine: String
        get() = "$dateRange · ${collected ?: "—"}"
    val seasonLine: String
        get() = "$seasonName · ${stats.cells} CELLS"
    val countsLine: String
        get() = "LEAVES ${stats.leaves} · THORNS ${stats.thorns} · DRUPES ${stats.drupes} · SCARS ${stats.scars}"
    val footer: String
        get() = "HERBARIUM · PLATE $plateNumber"
}
