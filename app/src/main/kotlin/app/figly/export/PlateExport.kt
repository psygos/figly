package app.figly.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.MediaStore
import androidx.core.content.res.ResourcesCompat
import app.figly.R
import app.figly.core.Fig
import app.figly.core.Plate
import app.figly.core.PlateLabel
import app.figly.core.PlateSvg
import app.figly.core.WeekKeys
import app.figly.data.FigRepository
import app.figly.data.WeekEntity
import app.figly.data.statsFromJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports — user-initiated only, via MediaStore. The PNG is the plate at
 * 2048 px; the SVG is the same pressing, vector-true.
 */
object PlateExport {

    suspend fun exportBoth(
        context: Context,
        fig: Fig,
        week: WeekEntity,
        label: PlateLabel,
    ): String = withContext(Dispatchers.IO) {
        val png = renderPng(context, fig, week.seed.toUInt(), label)
        savePng(context, "${label.figId}.png", png)
        png.recycle()
        saveSvg(context, "${label.figId}.svg", PlateSvg.render(fig, week.seed.toUInt(), label))
        "EXPORTED → PICTURES/FIGLY"
    }

    suspend fun exportAll(context: Context): Int {
        val repo = FigRepository.get(context)
        val weeks = repo.pressedWeeks()
        for (week in weeks) {
            val fig = repo.figOf(week)
            exportBoth(context, fig, week, labelOf(week))
        }
        return weeks.size
    }

    fun labelOf(week: WeekEntity): PlateLabel = PlateLabel(
        figId = WeekKeys.figId(week.isoWeek),
        dateRange = WeekKeys.dateRangeLabel(week.isoWeek),
        collected = week.collected,
        seasonName = week.seasonName,
        seasonHex = week.seasonTint,
        stats = statsFromJson(week.statsJson),
        plateNumber = WeekKeys.plateNumber(week.isoWeek),
    )

    /** The reference plate at 2048 px on the long edge. Matte ink only. */
    private fun renderPng(context: Context, fig: Fig, seed: UInt, label: PlateLabel): Bitmap {
        val h = 2048
        val w = (h * Plate.WIDTH / Plate.HEIGHT).toInt()
        val s = (h / Plate.HEIGHT).toFloat()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val ground = Color.parseColor(PlateSvg.Tokens.GROUND)
        val plate = Color.parseColor(PlateSvg.Tokens.PLATE)
        val hairline = Color.parseColor(PlateSvg.Tokens.HAIRLINE)
        val ink = Color.parseColor(PlateSvg.Tokens.INK)
        val inkDim = Color.parseColor(PlateSvg.Tokens.INK_DIM)
        val inkFaint = Color.parseColor(PlateSvg.Tokens.INK_FAINT)
        val tint = Color.parseColor(label.seasonHex)

        c.drawColor(ground)
        paint.color = plate
        c.drawRect(28 * s, 28 * s, (28 + 704) * s, (28 + 984) * s, paint)
        paint.color = hairline
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s
        c.drawRect(28 * s, 28 * s, (28 + 704) * s, (28 + 984) * s, paint)

        // Corner ticks.
        paint.color = inkDim
        fun tick(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            c.drawLine(x1 * s, y1 * s, x2 * s, y2 * s, paint)
            c.drawLine(x2 * s, y2 * s, x3 * s, y3 * s, paint)
        }
        tick(52f, 64f, 52f, 52f, 64f, 52f)
        tick(708f, 64f, 708f, 52f, 696f, 52f)
        tick(52f, 976f, 52f, 988f, 64f, 988f)
        tick(708f, 976f, 708f, 988f, 696f, 988f)
        paint.style = Paint.Style.FILL

        // The dot field.
        for (d in Plate.dots(fig, seed)) {
            paint.color = when (d.ink) {
                Plate.Ink.PAPER, Plate.Ink.SOIL, Plate.Ink.SCAR -> inkFaint
                Plate.Ink.DRUPE -> tint
                else -> ink
            }
            paint.alpha = (d.alpha * 255).toInt()
            c.drawCircle((d.x * s).toFloat(), (d.y * s).toFloat(), (d.r * s).toFloat(), paint)
        }
        paint.alpha = 255

        // Label block, in the bundled faces.
        val grotesk = ResourcesCompat.getFont(context, R.font.space_grotesk)
            ?.let { Typeface.create(it, 500, false) } ?: Typeface.DEFAULT
        val mono = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE

        paint.typeface = grotesk
        paint.textSize = 30 * s
        paint.letterSpacing = 4f / 30f
        paint.color = ink
        c.drawText(label.figId, 88 * s, 752 * s, paint)

        paint.color = tint
        c.drawRect(88 * s, 764 * s, (88 + 236) * s, (764 + 2) * s, paint)
        c.drawRect(88 * s, 824 * s, (88 + 18) * s, (824 + 3) * s, paint)

        paint.typeface = mono
        paint.textSize = 13 * s
        paint.letterSpacing = 2.5f / 13f
        paint.color = inkDim
        c.drawText(label.collectedLine, 88 * s, 804 * s, paint)
        c.drawText(label.seasonLine, 116 * s, 832 * s, paint)
        c.drawText(label.countsLine, 88 * s, 862 * s, paint)

        paint.textSize = 10 * s
        paint.letterSpacing = 3f / 10f
        paint.alpha = 178
        val footerW = paint.measureText(label.footer)
        c.drawText(label.footer, 672 * s - footerW, 986 * s, paint)

        return bmp
    }

    private fun savePng(context: Context, name: String, bmp: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/figly")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
        ) ?: return
        context.contentResolver.openOutputStream(uri)?.use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun saveSvg(context: Context, name: String, svg: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "image/svg+xml")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/figly")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
        ) ?: return
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(svg.toByteArray())
        }
    }
}
