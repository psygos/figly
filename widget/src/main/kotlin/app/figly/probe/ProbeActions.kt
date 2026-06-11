package app.figly.probe

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import app.figly.core.DayReading
import app.figly.data.FigRepository
import app.figly.data.UsageReadings
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Probe's hands. Every action mutates the draft (or seals) and repaints.
 * ≤ 6 taps to a sealed day; not one of them celebrated.
 */

val PARAM_VALUE = ActionParameters.Key<Int>("value")
val PARAM_DATE = ActionParameters.Key<String>("date")
val PARAM_CHANNEL = ActionParameters.Key<String>("channel")

private suspend fun repaint(context: Context) {
    ProbeWidget().updateAll(context)
}

class SetMoodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val v = parameters[PARAM_VALUE] ?: return
        FigRepository.get(context).updateDraft(LocalDate.now()) { copy(mood = v) }
        repaint(context)
    }
}

class SetEffortAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val v = parameters[PARAM_VALUE] ?: return
        FigRepository.get(context).updateDraft(LocalDate.now()) { copy(effort = v) }
        repaint(context)
    }
}

/** Accept the suggested night as read. */
class ConfirmSleepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = FigRepository.get(context)
        val today = LocalDate.now()
        val draft = repo.draft(today)
        val (bed, dur) = if (draft.bedMin != null && draft.durMin != null) {
            draft.bedMin!! to draft.durMin!!
        } else {
            val s = UsageReadings.suggestSleep(context, today) ?: return
            s.bedMinutesAfterNoon to s.durationMin
        }
        repo.updateDraft(today) { copy(bedMin = bed, durMin = dur, sleepConfirmed = true) }
        repaint(context)
    }
}

/** Tap SCREEN to override the instrument's reading. */
class ToggleScreenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = FigRepository.get(context)
        val today = LocalDate.now()
        val current = repo.draft(today).underBudget
            ?: UsageReadings.underBudget(context, repo.screenBudgetMin)
        repo.updateDraft(today) { copy(underBudget = !(current ?: false)) }
        repaint(context)
    }
}

class SealDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = FigRepository.get(context)
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val d = repo.draft(today)
        val mood = d.mood ?: return
        val bed = d.bedMin ?: return
        val dur = d.durMin ?: return
        val effort = d.effort ?: return
        val budget = d.underBudget
            ?: UsageReadings.underBudget(context, repo.screenBudgetMin, now)
            ?: return
        if (!d.sleepConfirmed) return
        repo.sealDay(
            today,
            DayReading(
                mood = mood,
                bedMinutesAfterNoon = bed,
                durationMin = dur,
                effort = effort,
                underBudget = budget,
            ),
            now,
        )
        repaint(context)
    }
}

/** An answered row is not a sealed row: tap its mark to amend it. */
class AmendAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val channel = parameters[PARAM_CHANNEL] ?: return
        val repo = FigRepository.get(context)
        val today = LocalDate.now()
        repo.updateDraft(today) {
            when (channel) {
                "mood" -> copy(mood = null)
                "effort" -> copy(effort = null)
                // Keep the night's values; just reopen the confirmation.
                "sleep" -> copy(sleepConfirmed = false)
                "screen" -> copy(underBudget = null)
                else -> this
            }
        }
        repaint(context)
    }
}

/** Grace: fill yesterday — hands the questions to the slim sheet. */
class GraceScarAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val date = parameters[PARAM_DATE] ?: return
        FigRepository.get(context).scarDay(LocalDate.parse(date))
        repaint(context)
    }
}
