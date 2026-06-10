package app.figly

import android.app.Activity
import android.content.Intent
import app.figly.core.DayReading
import app.figly.core.DaySlot
import app.figly.core.WeekKeys
import app.figly.data.FigRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

/**
 * Debug-only demo flora:
 *   adb shell am start -n app.figly/.MainActivity --ez plant_demo true
 * Absent from release builds (a no-op twin lives in src/release).
 */
object DemoSeed {

    fun plantIfAsked(activity: Activity, intent: Intent) {
        if (!intent.getBooleanExtra("plant_demo", false)) return
        intent.removeExtra("plant_demo") // consumed: recreate() must not replant
        runBlocking { plant(activity) }
    }

    private suspend fun plant(activity: Activity) {
        val repo = FigRepository.get(activity)
        val today = LocalDate.now()
        val thisMonday = WeekKeys.monday(today)

        fun day(mood: Int, bed: Int, dur: Int, effort: Int, under: Boolean) =
            DaySlot.Sealed(DayReading(mood, bed, dur, effort, under))

        val pasts: List<Pair<Int, List<DaySlot>>> = listOf(
            4 to listOf( // a verdant week
                day(4, 660, 470, 4, true), day(5, 645, 485, 2, true),
                day(4, 690, 440, 3, false), day(5, 660, 480, 5, true),
                day(3, 720, 410, 2, true), day(4, 660, 465, 4, false),
                day(5, 645, 490, 2, true),
            ),
            3 to listOf( // an ashfall week with gaps
                day(2, 870, 250, 1, false), day(1, 900, 230, 1, false),
                DaySlot.Missed, day(2, 850, 280, 2, false),
                DaySlot.Missed, day(1, 880, 240, 1, false),
                day(2, 840, 300, 1, false),
            ),
            2 to listOf( // a temperate week
                day(3, 690, 444, 2, true), day(4, 645, 460, 4, false),
                day(2, 795, 340, 1, false), DaySlot.Missed,
                day(4, 660, 432, 2, true), day(2, 822, 290, 5, false),
                day(3, 702, 408, 2, true),
            ),
        )

        for ((weeksAgo, slots) in pasts) {
            val monday = thisMonday.minusWeeks(weeksAgo.toLong())
            val key = WeekKeys.isoWeekKey(monday)
            if (repo.pressedWeek(key) != null) continue
            // Day rows too, so node annotations read true on demo plates.
            slots.forEachIndexed { i, slot ->
                val date = monday.plusDays(i.toLong())
                when (slot) {
                    is DaySlot.Sealed -> repo.sealDay(date, slot.reading)
                    is DaySlot.Missed -> repo.scarDay(date)
                }
            }
            repo.pressWeek(key, slots, collected = "DELHI", ceremonyPending = false)
        }

        // This week: three sealed days, today still listening.
        val seals = listOf(
            Triple(0L, 4, true), Triple(1L, 3, false), Triple(2L, 5, true),
        )
        for ((offset, mood, under) in seals) {
            val date = thisMonday.plusDays(offset)
            if (date >= today) break
            repo.sealDay(
                date,
                DayReading(
                    mood = mood,
                    bedMinutesAfterNoon = 660 + (offset * 30).toInt(),
                    durationMin = 430 + (offset * 20).toInt(),
                    effort = if (offset == 1L) 4 else 2,
                    underBudget = under,
                ),
            )
        }
        activity.recreate()
    }
}
