package app.figly.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowTest {

    private val seed = weekSeed("2026-W23", GoldenWeeks.SALT)

    @Test
    fun `growth is deterministic — two runs, identical figs`() {
        val a = Grow.grow(seed, GoldenWeeks.MIXED)
        val b = Grow.grow(seed, GoldenWeeks.MIXED)
        assertEquals(a, b)
    }

    @Test
    fun `growth is append-only — a partial week is a prefix of the full week`() {
        for (k in 0..GoldenWeeks.MIXED.size) {
            val partial = Grow.grow(seed, GoldenWeeks.MIXED.take(k))
            val full = Grow.grow(seed, GoldenWeeks.MIXED)
            val fullPrefix = full.cells.filter { it.day <= k }
            // Drupe conversion mutates a day's own terminal node only, so
            // day-k cells never change once day k is grown.
            assertEquals(fullPrefix, partial.cells)
        }
    }

    @Test
    fun `every cell lies in the mask and off the soil band`() {
        for (days in listOf(GoldenWeeks.AURIC, GoldenWeeks.ASHFALL, GoldenWeeks.MIXED)) {
            val fig = Grow.grow(seed, days)
            for (c in fig.cells) {
                assertTrue("${c.pos} in mask", Matrix.inMask(c.pos.row, c.pos.col))
                assertTrue("${c.pos} off soil", !Matrix.isSoil(c.pos.row, c.pos.col))
            }
        }
    }

    @Test
    fun `no two cells share a position`() {
        for (days in listOf(GoldenWeeks.AURIC, GoldenWeeks.ASHFALL, GoldenWeeks.MIXED)) {
            val fig = Grow.grow(seed, days)
            assertEquals(fig.cells.size, fig.cells.map { it.pos }.toSet().size)
        }
    }

    @Test
    fun `seed cell is always first, wood, day zero`() {
        val fig = Grow.grow(seed, emptyList())
        assertEquals(1, fig.cells.size)
        val s = fig.cells.first()
        assertEquals(Pos(Matrix.SEED_ROW, Matrix.SEED_COL), s.pos)
        assertEquals(CellType.WOOD, s.type)
        assertEquals(0, s.day)
    }

    @Test
    fun `missed days leave exactly one scar and no ornaments`() {
        val fig = Grow.grow(seed, listOf(DaySlot.Missed))
        val day1 = fig.cellsOfDay(1)
        assertEquals(1, day1.size)
        assertEquals(CellType.SCAR, day1.first().type)
    }

    @Test
    fun `a thorn grows at effort 4 and above, never below`() {
        // Bare day (no leaves) so the thorn channel is observed alone —
        // a leaf pair may legitimately block a short day's thorn.
        fun day(effort: Int) = DaySlot.Sealed(
            DayReading(mood = 3, bedMinutesAfterNoon = 660, durationMin = 200, effort = effort, underBudget = false),
        )
        for (e in 1..5) {
            val fig = Grow.grow(seed, listOf(day(e)))
            val thorns = fig.cellsOfDay(1).count { it.type == CellType.THORN }
            if (e >= 4) assertEquals("effort $e", 1, thorns) else assertEquals("effort $e", 0, thorns)
        }
    }

    @Test
    fun `under budget turns the day's terminal node into the drupe`() {
        val day = DaySlot.Sealed(
            DayReading(mood = 4, bedMinutesAfterNoon = 660, durationMin = 200, effort = 1, underBudget = true),
        )
        val fig = Grow.grow(seed, listOf(day))
        val day1 = fig.cellsOfDay(1)
        assertEquals(1, day1.count { it.type == CellType.DRUPE })
        // The drupe is the last-placed structural cell of the day.
        assertEquals(CellType.DRUPE, day1.last { it.type != CellType.LEAF && it.type != CellType.THORN }.type)
    }

    @Test
    fun `sleep shapes leaves — pair at 7h, single at 5h, bare below`() {
        fun day(dur: Int) = DaySlot.Sealed(
            DayReading(mood = 4, bedMinutesAfterNoon = 630, durationMin = dur, effort = 1, underBudget = false),
        )
        val pair = Grow.grow(seed, listOf(day(7 * 60))).cellsOfDay(1).count { it.type == CellType.LEAF }
        val single = Grow.grow(seed, listOf(day(6 * 60))).cellsOfDay(1).count { it.type == CellType.LEAF }
        val bare = Grow.grow(seed, listOf(day(4 * 60))).cellsOfDay(1).count { it.type == CellType.LEAF }
        assertEquals(2, pair)
        assertEquals(1, single)
        assertEquals(0, bare)
    }

    @Test
    fun `internode lengths follow the 1-5 scale`() {
        assertEquals(1, internodeLength(1))
        assertEquals(1, internodeLength(2))
        assertEquals(2, internodeLength(3))
        assertEquals(3, internodeLength(4))
        assertEquals(4, internodeLength(5))
    }

    @Test
    fun `seasons band the 1-5 mean in five equal climates`() {
        assertEquals(Season.ASHFALL, Season.fromMeanMood(1.0))
        assertEquals(Season.OVERCAST, Season.fromMeanMood(1.8))
        assertEquals(Season.TEMPERATE, Season.fromMeanMood(2.6))
        assertEquals(Season.VERDANT, Season.fromMeanMood(3.4))
        assertEquals(Season.AURIC, Season.fromMeanMood(4.2))
        assertEquals(Season.AURIC, Season.fromMeanMood(5.0))
        assertEquals(Season.BARREN, Season.fromMeanMood(null))
    }
}
