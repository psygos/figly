package app.figly.core

/**
 * Probe's live preview: what today will add to the fig, as a small stamp.
 * Recomputed from current partial answers — unanswered channels simply
 * don't appear yet. The stamp is the day's growth simulated against the
 * real fig, then framed into a 5×5 field.
 */
object Stamp {
    const val SIZE = 5

    data class Dot(val col: Int, val row: Int, val type: CellType)

    /**
     * Simulate today's contribution given the week so far.
     *
     * @param seed     the week's seed
     * @param daysSoFar resolved slots before today (sealed or missed)
     * @param mood     1–5 or null if unanswered (internode defaults to 1 segment)
     * @param bedMin   bed minutes-after-noon, null if unanswered (no tropism yet)
     * @param durMin   sleep duration, null if unanswered (no leaves yet)
     * @param effort   1–5, null if unanswered (no thorn yet)
     * @param underBudget null if unresolved (no drupe yet)
     */
    fun preview(
        seed: UInt,
        daysSoFar: List<DaySlot>,
        mood: Int?,
        bedMin: Int?,
        durMin: Int?,
        effort: Int?,
        underBudget: Boolean?,
    ): List<Dot> {
        val reading = DayReading(
            mood = mood ?: 1,
            bedMinutesAfterNoon = bedMin ?: 630,
            durationMin = durMin ?: 0,
            effort = effort ?: 1,
            underBudget = underBudget ?: false,
        )
        val before = Grow.grow(seed, daysSoFar)
        val after = Grow.grow(seed, daysSoFar + DaySlot.Sealed(reading))
        val today = after.cells.filter { it.day == daysSoFar.size + 1 }
        if (today.isEmpty()) return emptyList()

        // Frame the day's cells into the 5×5 field, anchored bottom-center
        // on the day's first cell, clipped if a wild day overflows.
        val anchor = today.first().pos
        return today.mapNotNull { c ->
            val col = c.pos.col - anchor.col + SIZE / 2
            val row = c.pos.row - anchor.row + (SIZE - 1)
            if (col in 0 until SIZE && row in 0 until SIZE) Dot(col, row, c.type) else null
        }
    }

    /** The sealed day's stamp — its true cells, framed the same way. */
    fun sealedStamp(seed: UInt, daysThroughToday: List<DaySlot>): List<Dot> {
        val fig = Grow.grow(seed, daysThroughToday)
        val today = fig.cells.filter { it.day == daysThroughToday.size }
        if (today.isEmpty()) return emptyList()
        val anchor = today.first().pos
        return today.mapNotNull { c ->
            val col = c.pos.col - anchor.col + SIZE / 2
            val row = c.pos.row - anchor.row + (SIZE - 1)
            if (col in 0 until SIZE && row in 0 until SIZE) Dot(col, row, c.type) else null
        }
    }
}
