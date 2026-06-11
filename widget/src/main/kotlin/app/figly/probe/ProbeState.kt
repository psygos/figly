package app.figly.probe

import android.content.Context
import app.figly.core.DaySlot
import app.figly.core.Stamp
import app.figly.core.WeekKeys
import app.figly.data.FigRepository
import app.figly.data.UsageReadings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Probe's mind: everything the widget needs to ask one day's questions,
 * computed fresh on every render. Six states; no celebration in any of them.
 */
data class ProbeState(
    val kind: Kind,
    val dayName: String,          // WED
    val grace: LocalDate?,        // yesterday, if unresolved and in grace
    val mood: Int?,               // 1..5
    val bedMin: Int?,             // minutes after noon
    val durMin: Int?,
    val sleepConfirmed: Boolean,
    val sleepIsSuggestion: Boolean,
    val effort: Int?,             // 1..5
    val effortLabel: String,
    val underBudget: Boolean?,    // resolved (draft override or auto)
    val budgetIsAuto: Boolean,
    val budgetNeedsHand: Boolean, // no permission, no override
    val sealable: Boolean,
    val stamp: List<Stamp.Dot>,
    val figId: String,            // for PRESSING
    val sealedStamp: List<Stamp.Dot>,
) {
    enum class Kind { EARLY, ASKING, SEALED, PRESSING }

    /** Of the five readings — bed time and duration confirm together as two. */
    val answered: Int
        get() = listOf(mood != null, effort != null, underBudget != null).count { it } +
            if (sleepConfirmed) 2 else 0
}

object Probe {

    suspend fun state(context: Context, now: ZonedDateTime = ZonedDateTime.now()): ProbeState {
        val repo = FigRepository.get(context)
        repo.resolveLapsedGraces(now)
        repo.pressIfDue(now)

        val today = now.toLocalDate()
        val weekKey = WeekKeys.isoWeekKey(today)
        val dayName = WeekKeys.DAY_NAMES[WeekKeys.dayIndex(today) - 1]
        val sealedToday = repo.todayRow(now) != null
        val grace = repo.graceDate(now)

        val draft = repo.draft(today)

        // Sleep: suggest from the night's longest screen-off gap; confirm,
        // never assume.
        var bed = draft.bedMin
        var dur = draft.durMin
        var suggestion = false
        if (!draft.sleepConfirmed && bed == null) {
            UsageReadings.suggestSleep(context, today)?.let {
                bed = it.bedMinutesAfterNoon
                dur = it.durationMin
                suggestion = true
            }
        }

        // Screen: auto-resolve at 23:00 or on seal, whichever comes first;
        // a tap can always override.
        val auto = UsageReadings.underBudget(context, repo.screenBudgetMin, now)
        val resolved = draft.underBudget ?: auto
        val budgetIsAuto = draft.underBudget == null && auto != null
        if (draft.underBudget == null && auto != null && now.hour >= 23) {
            repo.updateDraft(today) { copy(underBudget = auto) }
        }

        val slots = repo.daySlots(weekKey, today)
        val daysBeforeToday = slots.take(WeekKeys.dayIndex(today) - 1)
        val seed = repo.seedFor(weekKey)

        val stamp = Stamp.preview(
            seed = seed,
            daysSoFar = daysBeforeToday,
            mood = draft.mood,
            bedMin = bed,
            durMin = dur,
            effort = draft.effort,
            underBudget = resolved,
        )

        val kind = when {
            today.dayOfWeek == DayOfWeek.SUNDAY && sealedToday -> ProbeState.Kind.PRESSING
            sealedToday -> ProbeState.Kind.SEALED
            now.hour < 17 -> ProbeState.Kind.EARLY
            else -> ProbeState.Kind.ASKING
        }

        val sealable = draft.mood != null && draft.sleepConfirmed &&
            draft.effort != null && resolved != null

        return ProbeState(
            kind = kind,
            dayName = dayName,
            grace = grace,
            mood = draft.mood,
            bedMin = bed,
            durMin = dur,
            sleepConfirmed = draft.sleepConfirmed,
            sleepIsSuggestion = suggestion,
            effort = draft.effort,
            effortLabel = repo.effortLabel,
            underBudget = resolved,
            budgetIsAuto = budgetIsAuto,
            budgetNeedsHand = resolved == null,
            sealable = sealable,
            stamp = stamp,
            figId = WeekKeys.figId(weekKey),
            sealedStamp = if (sealedToday) {
                Stamp.sealedStamp(seed, slots.take(WeekKeys.dayIndex(today)))
            } else emptyList(),
        )
    }

    /** micro-labels are tracked with thin spaces — remoteviews has no kerning. */
    fun tracked(s: String): String =
        s.uppercase(Locale.ROOT).toCharArray().joinToString(" ")

    /** "01:10" from minutes-after-noon. */
    fun clockOf(minAfterNoon: Int): String {
        val abs = (12 * 60 + minAfterNoon) % (24 * 60)
        return "%02d:%02d".format(Locale.ROOT, abs / 60, abs % 60)
    }

    /** Wake clock from bed + duration. */
    fun wakeOf(bedMinAfterNoon: Int, durationMin: Int): String =
        clockOf(bedMinAfterNoon + durationMin)
}
