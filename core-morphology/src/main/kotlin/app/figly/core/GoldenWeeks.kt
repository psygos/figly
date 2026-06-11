package app.figly.core

/**
 * The three golden weeks — fixtures the implementation is forever held to.
 * All scales 1–5. Bed times in minutes after noon (630 = 22:30).
 */
object GoldenWeeks {

    const val SALT = "golden"

    /** All days lived at full vigor: 23:00 nights, 8 h sleep, both efforts present. */
    val AURIC: List<DaySlot> = listOf(
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, body = 4, mind = 3, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, body = 2, mind = 5, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, body = 5, mind = 2, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, body = 2, mind = 4, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, body = 4, mind = 2, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, body = 3, mind = 3, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, body = 3, mind = 3, underBudget = true)),
    )

    /** Stunted and drooping: 02:30 nights, 4 h sleep, no effort of either kind, two gaps. */
    val ASHFALL: List<DaySlot> = listOf(
        DaySlot.Sealed(DayReading(mood = 1, bedMinutesAfterNoon = 870, durationMin = 240, body = 1, mind = 1, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 2, bedMinutesAfterNoon = 870, durationMin = 240, body = 1, mind = 2, underBudget = false)),
        DaySlot.Missed,
        DaySlot.Sealed(DayReading(mood = 1, bedMinutesAfterNoon = 870, durationMin = 240, body = 2, mind = 1, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 2, bedMinutesAfterNoon = 870, durationMin = 240, body = 1, mind = 2, underBudget = false)),
        DaySlot.Missed,
        DaySlot.Sealed(DayReading(mood = 1, bedMinutesAfterNoon = 870, durationMin = 240, body = 1, mind = 1, underBudget = false)),
    )

    /** The reference plate's week: a true mixed week with one gap. */
    val MIXED: List<DaySlot> = listOf(
        DaySlot.Sealed(DayReading(mood = 3, bedMinutesAfterNoon = 690, durationMin = 444, body = 2, mind = 4, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 4, bedMinutesAfterNoon = 645, durationMin = 460, body = 4, mind = 2, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 2, bedMinutesAfterNoon = 795, durationMin = 340, body = 1, mind = 2, underBudget = false)),
        DaySlot.Missed,
        DaySlot.Sealed(DayReading(mood = 4, bedMinutesAfterNoon = 660, durationMin = 432, body = 2, mind = 3, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 2, bedMinutesAfterNoon = 822, durationMin = 290, body = 5, mind = 1, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 3, bedMinutesAfterNoon = 702, durationMin = 408, body = 2, mind = 2, underBudget = true)),
    )
}
