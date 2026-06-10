package app.figly.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import app.figly.core.Fig
import app.figly.core.FigCell
import app.figly.core.Matrix
import app.figly.core.Plate
import app.figly.core.Pos
import app.figly.core.Season
import kotlin.math.roundToInt

/**
 * The dot field of a plate, drawn from the same geometry that presses the
 * SVG exports and the thumbnails. Matte ink: no glow, no blur, no shadow,
 * no gradient — ever.
 *
 * [livingTip] is Herbarium's one concession to life: on This Week, the
 * awaiting position may pulse. Pressed plates never move.
 */
@Composable
fun PlateDots(
    fig: Fig,
    seed: UInt,
    season: Season,
    modifier: Modifier = Modifier,
    livingTip: Pos? = null,
    onCellTap: ((FigCell?) -> Unit)? = null,
) {
    val dots = remember(fig, seed) { Plate.dots(fig, seed) }
    val cellsByPos = remember(fig) { fig.cells.associateBy { it.pos } }
    val reduceMotion = rememberReducedMotion()

    val pulse = if (livingTip != null && !reduceMotion) {
        rememberInfiniteTransition(label = "awaiting")
            .animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(1000, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "awaiting",
            ).value
    } else 0.6f

    Canvas(
        modifier = modifier.let { m ->
            if (onCellTap == null) m
            else m.pointerInput(fig) {
                detectTapsToCells(cellsByPos) { onCellTap(it) }
            }
        },
    ) {
        // The dot field spans the full composable width at plate proportions.
        val scale = size.width / Plate.WIDTH.toFloat()
        val seasonColor = Color(android.graphics.Color.parseColor(season.hex))

        for (d in dots) {
            val color = when (d.ink) {
                Plate.Ink.PAPER, Plate.Ink.SOIL, Plate.Ink.SCAR -> Ink.inkFaint
                Plate.Ink.DRUPE -> seasonColor
                else -> Ink.ink
            }
            drawCircle(
                color = color.copy(alpha = (color.alpha * d.alpha).toFloat()),
                radius = (d.r * scale).toFloat(),
                center = Offset((d.x * scale).toFloat(), ((d.y - Plate.GRID_Y + 24) * scale).toFloat()),
            )
        }

        if (livingTip != null) {
            val cx = (Plate.GRID_X + livingTip.col * Plate.PITCH) * scale
            val cy = ((livingTip.row * Plate.PITCH) + 24) * scale
            drawCircle(
                color = Ink.ink.copy(alpha = 0.15f + 0.45f * pulse),
                radius = (Plate.R_PAPER * 1.9 * scale).toFloat(),
                center = Offset(cx.toFloat(), cy.toFloat()),
            )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapsToCells(
    cells: Map<Pos, FigCell>,
    onTap: (FigCell?) -> Unit,
) {
    detectTapGestures(
        onTap = { offset: Offset ->
            val scale = size.width / Plate.WIDTH.toFloat()
            val col = ((offset.x / scale - Plate.GRID_X) / Plate.PITCH).roundToInt()
            val row = (((offset.y / scale) - 24) / Plate.PITCH).roundToInt()
            val pos = Pos(row, col)
            onTap(if (Matrix.inMask(row, col)) cells[pos] else null)
        },
    )
}

/** Honor the animator scale: when motion is off, everything is instant. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** The height a PlateDots canvas needs for a given width: the dot field rows. */
fun plateDotsAspect(): Float {
    // 13 rows of pitch + margins, in plate units.
    val h = (Matrix.SIZE - 1) * Plate.PITCH + 48.0
    return (Plate.WIDTH / h).toFloat()
}
