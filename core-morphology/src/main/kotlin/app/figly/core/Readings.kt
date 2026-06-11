package app.figly.core

/**
 * One day's six readings. All scales run 1–5.
 *
 * Each reading shapes exactly one morphological channel — this 1:1 mapping
 * is the product's legibility, never blend channels:
 *
 *   mood 1–5        → internode length (vigor)
 *   sleep duration  → leaves (rest)
 *   sleep start     → tropism (bend) — early nights climb, late nights droop
 *   body 1–5        → thorn pointing down-outward (earth), grown at 4+
 *   mind 1–5        → thorn pointing up-outward (sky), grown at 4+
 *   under budget    → drupe (discipline fruit)
 *   missed check-in → scar — honest gaps
 */
data class DayReading(
    /** How the day felt, 1–5. */
    val mood: Int,
    /** Bed time, in minutes after noon local (0 = 12:00, 630 = 22:30, 900 = 03:00). */
    val bedMinutesAfterNoon: Int,
    /** Sleep duration in minutes. */
    val durationMin: Int,
    /** Physical effort, 1–5. A thorn grows downward at 4 and above. */
    val body: Int,
    /** Mental effort, 1–5. A thorn grows skyward at 4 and above. */
    val mind: Int,
    /** Screen time stayed under budget. */
    val underBudget: Boolean,
) {
    init {
        require(mood in 1..5) { "mood is a 1–5 scale" }
        require(body in 1..5) { "body is a 1–5 scale" }
        require(mind in 1..5) { "mind is a 1–5 scale" }
    }
}

/** A day in a week is either sealed with its readings, or missed. */
sealed interface DaySlot {
    data class Sealed(val reading: DayReading) : DaySlot
    data object Missed : DaySlot
}

/**
 * Internode length: how many segments a day grows.
 * 1→1 · 2→1 · 3→2 · 4→3 · 5→4 — both 1 and 2 are stunted; bad is bad.
 */
fun internodeLength(mood: Int): Int = maxOf(1, mood - 1)

/** Thorns mark days of real exertion — of either kind. */
fun growsThorn(effort: Int): Boolean = effort >= 4

/** Leaf habit from sleep duration. */
enum class LeafHabit { PAIR, SINGLE, BARE }

fun leafHabit(durationMin: Int): LeafHabit = when {
    durationMin >= 7 * 60 -> LeafHabit.PAIR
    durationMin >= 5 * 60 -> LeafHabit.SINGLE
    else -> LeafHabit.BARE
}

/** Bed-time lateness: minutes after 22:30, clamped to 03:00. Drives tropism. */
fun lateness(bedMinutesAfterNoon: Int): Int =
    (bedMinutesAfterNoon - 630).coerceIn(0, 270)
