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
    fun `auric week — tall, lush, every channel present`() {
        val fig = grown("2026-W21", GoldenWeeks.AURIC)
        assertEquals(
            FigStats(
                cells = 41, wood = 23, leaves = 11, thorns = 2, drupes = 5,
                scars = 0, heightRow = 0, meanMood = 5.0, season = Season.AURIC,
            ),
            fig.stats,
        )
        // Predominantly vertical: the crown reaches the sky.
        assertEquals(0, fig.stats.heightRow)
        assertTrue("vigorous weeks read vigorous", fig.stats.wood >= 20)
        assertEquals(
            """
            |        W W W W D
            |    · · · · l · W l D
            |  · · · · · · · W · t W
            |  · · · · · · l · W W W
            |· · · · · · · · W D · l ·
            |· · · · · · · l W l l · ·
            |· · · · · · · · W · · W D
            |· · · · · · W D · · W · l
            |· · · · · l W l t W · · ·
            |  · · · · · W · l W · ·
            |  · · · · · W W W · · ·
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
                cells = 21, wood = 10, leaves = 5, thorns = 2, drupes = 3,
                scars = 1, heightRow = 0, meanMood = 3.0, season = Season.TEMPERATE,
            ),
            fig.stats,
        )
        assertEquals(
            """
            |        · · D W W
            |    · · · · · · t D W
            |  · · · · · · · · · W ·
            |  · · · · · · · · x · ·
            |· · · · · · · · l W · · ·
            |· · · · · · · l t W · · ·
            |· · · · · · · · W · · · ·
            |· · · · · · · W · l · · ·
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
