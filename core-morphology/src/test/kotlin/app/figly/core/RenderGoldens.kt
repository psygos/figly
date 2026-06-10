package app.figly.core

import java.io.File

/**
 * Dev tool, not a test: renders the golden weeks (and a small salt sweep)
 * to SVG plates + ASCII so the grammar can be looked at and tuned.
 *
 *   ../gradlew :core-morphology:renderGoldens
 */
object RenderGoldens {

    fun ascii(fig: Fig): String {
        val byPos = fig.cells.associateBy { it.pos.key }
        return buildString {
            for (r in 0 until Matrix.SIZE) {
                val line = StringBuilder()
                for (c in 0 until Matrix.SIZE) {
                    val ch = when {
                        !Matrix.inMask(r, c) -> ' '
                        byPos.containsKey(r * Matrix.SIZE + c) -> when (byPos[r * Matrix.SIZE + c]!!.type) {
                            CellType.WOOD -> 'W'
                            CellType.LEAF -> 'l'
                            CellType.THORN -> 't'
                            CellType.DRUPE -> 'D'
                            CellType.SCAR -> 'x'
                        }
                        Matrix.isSoil(r, c) -> '~'
                        else -> '·'
                    }
                    line.append(ch).append(' ')
                }
                append(line.toString().trimEnd())
                append('\n')
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.getOrElse(0) { "/tmp/figly-plates" })
        out.mkdirs()

        fun render(name: String, key: String, days: List<DaySlot>, salt: String, collected: String?) {
            val seed = weekSeed(key, salt)
            val fig = Grow.grow(seed, days)
            val label = PlateLabel(
                figId = WeekKeys.figId(key),
                dateRange = WeekKeys.dateRangeLabel(key),
                collected = collected,
                seasonName = fig.stats.season.name,
                seasonHex = fig.stats.season.hex,
                stats = fig.stats,
                plateNumber = WeekKeys.plateNumber(key),
            )
            File(out, "$name.svg").writeText(PlateSvg.render(fig, seed, label))
            println("== $name  seed=$seed  ${fig.stats}")
            println(ascii(fig))
        }

        render("golden-auric", "2026-W21", GoldenWeeks.AURIC, GoldenWeeks.SALT, "DELHI")
        render("golden-ashfall", "2026-W22", GoldenWeeks.ASHFALL, GoldenWeeks.SALT, "DELHI")
        render("golden-mixed", "2026-W23", GoldenWeeks.MIXED, GoldenWeeks.SALT, "DELHI")

        // A sweep: the same mixed week under other salts — form must stay
        // data-dominated while the jitter breathes.
        if (args.getOrNull(1) == "sweep") {
            for (i in 1..6) {
                render("sweep-mixed-$i", "2026-W23", GoldenWeeks.MIXED, "sweep$i", "DELHI")
            }
        }
    }
}
