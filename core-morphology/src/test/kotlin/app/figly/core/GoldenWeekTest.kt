package app.figly.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three golden weeks, pinned exactly. These snapshots are the contract:
 * if grow() changes, these fail, and changing them is a product decision,
 * not a refactor.
 */
class GoldenWeekTest {

    private fun grown(key: String, days: List<DaySlot>): Fig =
        Grow.grow(weekSeed(key, GoldenWeeks.SALT), days)

    @Test
    fun `auric week — tall, lush, every channel present, both efforts`() {
        val fig = grown("2026-W21", GoldenWeeks.AURIC)
        assertEquals(
            FigStats(
                cells = 42, wood = 21, leaves = 12, thorns = 4, drupes = 5,
                scars = 0, heightRow = 0, meanMood = 5.0, season = Season.AURIC,
            ),
            fig.stats,
        )
        // Predominantly vertical: the crown reaches the sky.
        assertEquals(0, fig.stats.heightRow)
        assertTrue("vigorous weeks read vigorous", fig.stats.wood >= 20)
        assertEquals(
            """
            |        D W t W W
            |    · · l W · · W W D
            |  · · · l W l · l · W t
            |  · · · · W · · t l W l
            |· · · · D W · · W D W · ·
            |· · · · l W l l W l · · ·
            |· · · · · W · · W · · · ·
            |· · · · · · W D · · · · ·
            |· · · · · l W l t · · · ·
            |  · · · · · W · · · · ·
            |  · · · · · W · · · · ·
            |    · ~ ~ ~ ~ ~ ~ ~ ·
            |        · · · · ·
            |""".trimMargin(),
            RenderGoldens.ascii(fig),
        )
    }

    @Test
    fun `ashfall week — stunted, bare, drooping, honestly scarred`() {
        val fig = grown("2026-W22", GoldenWeeks.ASHFALL)
        assertEquals(
            FigStats(
                cells = 8, wood = 6, leaves = 0, thorns = 0, drupes = 0,
                scars = 2, heightRow = 5, meanMood = 1.4, season = Season.ASHFALL,
            ),
            fig.stats,
        )
        // Pronounced lateral droop, never the climb of a lived week.
        assertTrue("stunted", fig.stats.heightRow >= 5)
        assertTrue("droops sideways", fig.cells.maxOf { it.pos.col } >= 9)
        assertEquals(
            """
            |        · · · · ·
            |    · · · · · · · · ·
            |  · · · · · · · · · · ·
            |  · · · · · · · · · · ·
            |· · · · · · · · · · · · ·
            |· · · · · · · · · · W · ·
            |· · · · · · · · · x · · ·
            |· · · · · · · · · W · · ·
            |· · · · · · · · x W · · ·
            |  · · · · · · W W · · ·
            |  · · · · · W · · · · ·
            |    · ~ ~ ~ ~ ~ ~ ~ ·
            |        · · · · ·
            |""".trimMargin(),
            RenderGoldens.ascii(fig),
        )
    }

    @Test
    fun `mixed week — the reference fixture, counts pinned to its plate label`() {
        val fig = grown("2026-W23", GoldenWeeks.MIXED)
        assertEquals(
            FigStats(
                cells = 23, wood = 10, leaves = 6, thorns = 3, drupes = 3,
                scars = 1, heightRow = 0, meanMood = 3.0, season = Season.TEMPERATE,
            ),
            fig.stats,
        )
        assertEquals(
            """
            |        · · · D W
            |    · · · · · l · W D
            |  · · · · · · · · t W W
            |  · · · · · · · · · x ·
            |· · · · · · · · · · W · ·
            |· · · · · · · · l W · l ·
            |· · · · · · · · t W · · ·
            |· · · · · · t · W · l · ·
            |· · · · · · l D · · · · ·
            |  · · · · · · W · · · ·
            |  · · · · · W · l · · ·
            |    · ~ ~ ~ ~ ~ ~ ~ ·
            |        · · · · ·
            |""".trimMargin(),
            RenderGoldens.ascii(fig),
        )
    }
}
