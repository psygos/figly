package app.figly.core

import java.util.Locale

/**
 * Pure-Kotlin SVG plate renderer — the source of the repo's reference
 * plate and of Herbarium's SVG export. Matte ink only.
 */
object PlateSvg {

    object Tokens {
        const val GROUND = "#0A0B0D"
        const val PLATE = "#111317"
        const val HAIRLINE = "#20232A"
        const val INK = "#E7E2D5"
        const val INK_DIM = "#6B675C"
        const val INK_FAINT = "#3A3933"
    }

    fun render(fig: Fig, seed: UInt, label: PlateLabel): String {
        val sb = StringBuilder(16_384)
        sb.append(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 760 1040" font-family="'JetBrains Mono', ui-monospace, Menlo, Consolas, monospace">""",
        ).append('\n')
        sb.append("""<rect width="760" height="1040" fill="${Tokens.GROUND}"/>""").append('\n')
        sb.append(
            """<rect x="28" y="28" width="704" height="984" fill="${Tokens.PLATE}" stroke="${Tokens.HAIRLINE}" stroke-width="1"/>""",
        ).append('\n')

        // Corner ticks.
        sb.append("""<path d="M 52 64 L 52 52 L 64 52" fill="none" stroke="${Tokens.INK_DIM}" stroke-width="1"/>""").append('\n')
        sb.append("""<path d="M 708 64 L 708 52 L 696 52" fill="none" stroke="${Tokens.INK_DIM}" stroke-width="1"/>""").append('\n')
        sb.append("""<path d="M 52 976 L 52 988 L 64 988" fill="none" stroke="${Tokens.INK_DIM}" stroke-width="1"/>""").append('\n')
        sb.append("""<path d="M 708 976 L 708 988 L 696 988" fill="none" stroke="${Tokens.INK_DIM}" stroke-width="1"/>""").append('\n')

        val dots = Plate.dots(fig, seed)
        sb.append("<g>")
        for (d in dots.filter { it.ink == Plate.Ink.PAPER }) {
            sb.append(circle(d.x, d.y, d.r, Tokens.INK_FAINT, d.alpha))
        }
        sb.append("</g>\n<g>")
        for (d in dots.filter { it.ink != Plate.Ink.PAPER }) {
            val fill = when (d.ink) {
                Plate.Ink.SOIL, Plate.Ink.SCAR -> Tokens.INK_FAINT
                Plate.Ink.DRUPE -> label.seasonHex
                else -> Tokens.INK
            }
            sb.append(circle(d.x, d.y, d.r, fill, d.alpha))
        }
        sb.append("</g>\n")

        // Label block.
        sb.append(
            """<text x="88" y="752" font-family="'Space Grotesk', system-ui, sans-serif" font-size="30" font-weight="500" letter-spacing="4" fill="${Tokens.INK}">${label.figId}</text>""",
        ).append('\n')
        sb.append("""<rect x="88" y="764" width="236" height="2" fill="${label.seasonHex}"/>""").append('\n')
        sb.append(
            """<text x="88" y="804" font-size="13" letter-spacing="2.5" fill="${Tokens.INK_DIM}">${label.collectedLine}</text>""",
        ).append('\n')
        sb.append("""<rect x="88" y="824" width="18" height="3" fill="${label.seasonHex}"/>""").append('\n')
        sb.append(
            """<text x="116" y="832" font-size="13" letter-spacing="2.5" fill="${Tokens.INK_DIM}">${label.seasonLine}</text>""",
        ).append('\n')
        sb.append(
            """<text x="88" y="862" font-size="13" letter-spacing="2.5" fill="${Tokens.INK_DIM}">${label.countsLine}</text>""",
        ).append('\n')
        sb.append(
            """<text x="672" y="986" text-anchor="end" font-size="10" letter-spacing="3" fill="${Tokens.INK_DIM}" opacity="0.7">${label.footer}</text>""",
        ).append('\n')
        sb.append("</svg>")
        return sb.toString()
    }

    private fun circle(x: Double, y: Double, r: Double, fill: String, alpha: Double): String {
        val a = if (alpha >= 1.0) "" else """ opacity="${fmt(alpha, 2)}""""
        return """<circle cx="${fmt(x, 1)}" cy="${fmt(y, 1)}" r="${fmt(r, 1)}" fill="$fill"$a/>"""
    }

    private fun fmt(v: Double, places: Int): String =
        String.format(Locale.ROOT, "%.${places}f", v).trimEnd('0').trimEnd('.')
}
