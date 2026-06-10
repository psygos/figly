@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.figly.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.figly.R
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Ink — the whole design system. Two faces, one palette, hairlines never
 * thicker than a pixel. No Material anywhere; the press indication is a
 * 5 % dim, not a ripple.
 */
object Ink {
    val ground = Color(0xFF0A0B0D)
    val plate = Color(0xFF111317)
    val hairline = Color(0xFF20232A)
    val ink = Color(0xFFE7E2D5)
    val inkDim = Color(0xFF6B675C)
    val inkFaint = Color(0xFF3A3933)

    // 4pt grid.
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s12 = 48.dp

    val grotesk = FontFamily(
        Font(
            R.font.space_grotesk,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            R.font.space_grotesk,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
    )

    val mono = FontFamily(
        Font(R.font.jetbrains_mono, weight = FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, weight = FontWeight.Medium),
    )

    /** Display face: specimen IDs, the wordmark, large day numbers. */
    fun display(size: TextUnit, color: Color = ink, tracking: TextUnit = 0.04.em) = TextStyle(
        fontFamily = grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = size,
        letterSpacing = tracking,
        color = color,
    )

    /** Data face: every label, reading, annotation. */
    fun data(size: TextUnit = 13.sp, color: Color = inkDim) = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = size,
        letterSpacing = 0.08.em,
        color = color,
    )

    /** All-caps micro-labels, 10–11sp, 0.14em tracking. */
    fun micro(size: TextUnit = 10.sp, color: Color = inkDim) = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = size,
        letterSpacing = 0.14.em,
        color = color,
    )
}

@Composable
fun Label(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text = text, style = style, modifier = modifier, maxLines = maxLines)
}

@Composable
fun Micro(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.inkDim,
    size: TextUnit = 10.sp,
) {
    BasicText(
        text = text.uppercase(Locale.ROOT),
        style = Ink.micro(size, color),
        modifier = modifier,
    )
}

/**
 * The press indication of the whole product: content dims under the
 * finger. Quiet, instant, no circles.
 */
object QuietIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        QuietNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = "QuietIndication".hashCode()

    private class QuietNode(
        private val interactionSource: InteractionSource,
    ) : Modifier.Node(), DrawModifierNode {
        private var pressed = false

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    val now = when (interaction) {
                        is PressInteraction.Press -> true
                        is PressInteraction.Release, is PressInteraction.Cancel -> false
                        else -> pressed
                    }
                    if (now != pressed) {
                        pressed = now
                        invalidateDraw()
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (pressed) drawRect(Ink.ink.copy(alpha = 0.05f))
        }
    }
}
