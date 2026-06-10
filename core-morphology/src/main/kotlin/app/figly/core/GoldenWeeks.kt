package app.figly.core

/**
 * The three golden weeks — fixtures the implementation is forever held to.
 * All scales 1–5. Bed times in minutes after noon (630 = 22:30).
 */
object GoldenWeeks {

    const val SALT = "golden"

    /** All days lived at full vigor: 23:00 nights, 8 h sleep, 3 thorn days, 5 drupe days. */
    val AURIC: List<DaySlot> = listOf(
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, effort = 4, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, effort = 2, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, effort = 5, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, effort = 2, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, effort = 4, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, effort = 3, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 5, bedMinutesAfterNoon = 660, durationMin = 480, effort = 3, underBudget = true)),
    )

    /** Stunted and drooping: 02:30 nights, 4 h sleep, no effort, no budget, two gaps. */
    val ASHFALL: List<DaySlot> = listOf(
        DaySlot.Sealed(DayReading(mood = 1, bedMinutesAfterNoon = 870, durationMin = 240, effort = 1, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 2, bedMinutesAfterNoon = 870, durationMin = 240, effort = 1, underBudget = false)),
        DaySlot.Missed,
        DaySlot.Sealed(DayReading(mood = 1, bedMinutesAfterNoon = 870, durationMin = 240, effort = 2, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 2, bedMinutesAfterNoon = 870, durationMin = 240, effort = 1, underBudget = false)),
        DaySlot.Missed,
        DaySlot.Sealed(DayReading(mood = 1, bedMinutesAfterNoon = 870, durationMin = 240, effort = 1, underBudget = false)),
    )

    /** The reference plate's week: a true mixed week with one gap. */
    val MIXED: List<DaySlot> = listOf(
        DaySlot.Sealed(DayReading(mood = 3, bedMinutesAfterNoon = 690, durationMin = 444, effort = 2, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 4, bedMinutesAfterNoon = 645, durationMin = 460, effort = 4, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 2, bedMinutesAfterNoon = 795, durationMin = 340, effort = 1, underBudget = false)),
        DaySlot.Missed,
        DaySlot.Sealed(DayReading(mood = 4, bedMinutesAfterNoon = 660, durationMin = 432, effort = 2, underBudget = true)),
        DaySlot.Sealed(DayReading(mood = 2, bedMinutesAfterNoon = 822, durationMin = 290, effort = 5, underBudget = false)),
        DaySlot.Sealed(DayReading(mood = 3, bedMinutesAfterNoon = 702, durationMin = 408, effort = 2, underBudget = true)),
    )
}
