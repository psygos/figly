package app.figly.core

/** Dev tool: channel-survival statistics across many salts. */
object SweepTool {
    @JvmStatic
    fun main(args: Array<String>) {
        var thornsTotal = 0; var drupesTotal = 0; var leavesTotal = 0; var woodTotal = 0
        val n = 40
        for (i in 1..n) {
            val fig = Grow.grow(weekSeed("2026-W21", "salt$i"), GoldenWeeks.AURIC)
            thornsTotal += fig.stats.thorns; drupesTotal += fig.stats.drupes
            leavesTotal += fig.stats.leaves; woodTotal += fig.stats.wood
        }
        println("AURIC over $n salts — intended: thorns 3, drupes 5, leaves 14, wood ~29")
        println("mean thorns=%.1f drupes=%.1f leaves=%.1f wood=%.1f".format(
            thornsTotal / n.toDouble(), drupesTotal / n.toDouble(),
            leavesTotal / n.toDouble(), woodTotal / n.toDouble()))
        var minT = 99; var maxT = 0
        for (i in 1..n) {
            val s = Grow.grow(weekSeed("2026-W21", "salt$i"), GoldenWeeks.AURIC).stats
            if (s.thorns < minT) minT = s.thorns
            if (s.thorns > maxT) maxT = s.thorns
        }
        println("thorn range $minT..$maxT")
    }
}
