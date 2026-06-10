package app.figly.probe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.figly.core.CellType
import app.figly.core.Fig
import app.figly.core.Matrix
import app.figly.core.Stamp

/**
 * Probe's marks are monochrome — color belongs to Herbarium alone.
 * The drupe is simply the largest, brightest dot, as on the matrix.
 */
object StampBitmaps {

    private const val INK = 0xFFE7E2D5.toInt()
    private const val INK_FAINT = 0xFF3A3933.toInt()

    private fun radius(t: CellType, cell: Float): Float = when (t) {
        CellType.WOOD -> cell * 0.30f
        CellType.LEAF -> cell * 0.25f
        CellType.THORN -> cell * 0.18f
        CellType.SCAR -> cell * 0.25f
        CellType.DRUPE -> cell * 0.34f
    }

    private fun paintFor(t: CellType, p: Paint) {
        p.color = if (t == CellType.SCAR) INK_FAINT else INK
        p.alpha = when (t) {
            CellType.LEAF -> 204
            CellType.SCAR -> 255
            else -> 255
        }
    }

    /** The 5×5 day stamp: today's contribution, live. */
    fun stamp(dots: List<Stamp.Dot>, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cell = px / Stamp.SIZE.toFloat()

        // The faint dot paper, so an empty stamp still reads as paper.
        paint.color = INK_FAINT
        paint.alpha = 115
        for (r in 0 until Stamp.SIZE) for (c in 0 until Stamp.SIZE) {
            canvas.drawCircle((c + 0.5f) * cell, (r + 0.5f) * cell, cell * 0.07f, paint)
        }

        for (d in dots) {
            paintFor(d.type, paint)
            canvas.drawCircle(
                (d.col + 0.5f) * cell,
                (d.row + 0.5f) * cell,
                radius(d.type, cell),
                paint,
            )
        }
        return bmp
    }

    /** The pressed silhouette for PRESSING: the whole fig, tiny and still. */
    fun silhouette(fig: Fig, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cell = px / Matrix.SIZE.toFloat()

        paint.color = INK_FAINT
        paint.alpha = 230
        for (c in Matrix.SOIL_COLS) {
            canvas.drawCircle((c + 0.5f) * cell, (Matrix.SOIL_ROW + 0.5f) * cell, cell * 0.16f, paint)
        }
        for (cellOf in fig.cells) {
            paintFor(cellOf.type, paint)
            canvas.drawCircle(
                (cellOf.pos.col + 0.5f) * cell,
                (cellOf.pos.row + 0.5f) * cell,
                radius(cellOf.type, cell),
                paint,
            )
        }
        return bmp
    }
}
