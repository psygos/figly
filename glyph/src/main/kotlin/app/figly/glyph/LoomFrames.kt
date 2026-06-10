package app.figly.glyph

import app.figly.core.CellType
import app.figly.core.Fig
import app.figly.core.Matrix
import app.figly.core.Pos
import kotlin.math.PI
import kotlin.math.sin

/**
 * The brightness law, as frame math. Three layers — low: soil, mid: fig
 * body, top: bloom and pulse effects — written into reusable buffers;
 * nothing allocates per frame, a heartbeat frame builds in well under 8 ms.
 *
 *   soil 38 · scar 60 (never moves) · wood/thorn 126 (breath) ·
 *   leaf 176 ± 22 shimmer · drupe 205–250 slow pulse ·
 *   awaiting tip 80↔140 · today's commit blooms 0→255→natural
 */
class LoomFrames(private val len: Int = Matrix.SIZE) {

    val low = IntArray(len * len)
    val mid = IntArray(len * len)
    val top = IntArray(len * len)

    /** On a larger matrix the fig sits centered; on the (4a) Pro, off = 0. */
    private val off = ((len - Matrix.SIZE) / 2).coerceAtLeast(0)

    private fun idx(p: Pos) = (p.row + off) * len + (p.col + off)

    fun clearAll() {
        low.fill(0); mid.fill(0); top.fill(0)
    }

    /** Soil: the static ground truth. */
    fun soil(brightness: Int = SOIL) {
        for (c in Matrix.SOIL_COLS) low[idx(Pos(Matrix.SOIL_ROW, c))] = brightness
    }

    /**
     * The fig body at time [t] (ms). In the lively window leaves shimmer,
     * drupes pulse and a global breath moves the wood; a heartbeat frame
     * ([lively] = false) is the same fig, still.
     */
    fun figBody(fig: Fig, t: Long, lively: Boolean, throughDay: Int = 7) {
        val breath = if (lively) 1.0 + 0.045 * sin(t / 2700.0) else 1.0
        for (cell in fig.cells) {
            if (cell.day > throughDay) continue
            mid[idx(cell.pos)] = cellBrightness(cell.type, cell.pos, t, lively, breath)
        }
    }

    fun cellBrightness(type: CellType, pos: Pos, t: Long, lively: Boolean, breath: Double): Int =
        when (type) {
            CellType.SCAR -> SCAR // scars never move
            CellType.WOOD, CellType.THORN -> (WOOD * breath).toInt()
            CellType.LEAF ->
                if (lively) (LEAF + 22 * sin(t / 620.0 + phase(pos))).toInt()
                else LEAF
            CellType.DRUPE ->
                if (lively) (227.5 + 22.5 * sin(2 * PI * t / 1400.0 + phase(pos))).toInt()
                else DRUPE_STILL
        }.coerceIn(0, 255)

    /** The fig is listening: the empty cell above the tip, pulsing. */
    fun awaitingTip(fig: Fig, t: Long, lively: Boolean) {
        val p = awaitingPos(fig) ?: return
        top[idx(p)] = if (lively) (110 + 30 * sin(2 * PI * t / 2000.0)).toInt() else 110
    }

    /** A bare week: soil and the seed alone, pulsing slow. */
    fun seedOnly(t: Long, lively: Boolean) {
        val v = if (lively) (90 + 20 * sin(2 * PI * t / 2000.0)).toInt() else SEED
        mid[idx(Pos(Matrix.SEED_ROW, Matrix.SEED_COL))] = v.coerceIn(0, 255)
    }

    /** Direct write for effects (blooms, ceremonies). */
    fun set(layer: IntArray, p: Pos, v: Int) {
        layer[idx(p)] = v.coerceIn(0, 255)
    }

    companion object {
        const val SOIL = 38
        const val SCAR = 60
        const val WOOD = 126
        const val LEAF = 176
        const val DRUPE_STILL = 245
        const val SEED = 90

        private fun phase(p: Pos): Double = (p.row * 7 + p.col) * 0.7

        fun awaitingPos(fig: Fig): Pos? {
            val occupied = fig.cells.map { it.pos }.toHashSet()
            val tip = fig.tip
            return listOf(
                Pos(tip.row - 1, tip.col),
                Pos(tip.row - 1, tip.col + 1),
                Pos(tip.row - 1, tip.col - 1),
            ).firstOrNull {
                Matrix.inMask(it.row, it.col) && it !in occupied && !Matrix.isSoil(it.row, it.col)
            }
        }
    }
}
