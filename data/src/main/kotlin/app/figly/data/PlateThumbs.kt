package app.figly.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.figly.core.Fig
import app.figly.core.Plate
import java.io.File

/**
 * Pre-rastered drawer thumbnails: pure ink on plate, no labels — the
 * silhouettes are the index. Rendered once at press time so the drawer
 * scrolls past a hundred specimens without a thought.
 */
object PlateThumbs {

    private const val WIDTH = 456 // 760 × 0.6
    private const val HEIGHT = 624 // 1040 × 0.6 — same plate, smaller press

    private fun dir(context: Context) = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun file(context: Context, isoWeek: String): File = File(dir(context), "$isoWeek.png")

    fun write(context: Context, isoWeek: String, fig: Fig, seed: UInt) {
        val scale = WIDTH / Plate.WIDTH.toFloat()
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Thumbnails are the plate surface itself, edge to edge.
        canvas.drawColor(Color.parseColor("#111317"))

        for (d in Plate.dots(fig, seed)) {
            paint.color = when (d.ink) {
                Plate.Ink.PAPER, Plate.Ink.SOIL, Plate.Ink.SCAR -> Color.parseColor("#3A3933")
                Plate.Ink.DRUPE -> Color.parseColor(fig.stats.season.hex)
                else -> Color.parseColor("#E7E2D5")
            }
            paint.alpha = (d.alpha * 255).toInt()
            // The dot field occupies the upper plate; recenter it slightly
            // for the label-less crop.
            val x = (d.x * scale).toFloat()
            val y = ((d.y + 28.0) * scale).toFloat()
            canvas.drawCircle(x, y, (d.r * scale).toFloat(), paint)
        }

        file(context, isoWeek).outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bmp.recycle()
    }

    fun delete(context: Context, isoWeek: String) {
        file(context, isoWeek).delete()
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
