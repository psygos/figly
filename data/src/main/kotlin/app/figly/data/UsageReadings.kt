package app.figly.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The instrument's senses: sleep suggestion and screen-budget resolution
 * from UsageStats. Everything degrades gracefully to manual without the
 * permission — Probe asks, it never demands.
 */
object UsageReadings {

    fun hasPermission(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    data class SleepSuggestion(
        /** Bed time as minutes after noon of the night's start day. */
        val bedMinutesAfterNoon: Int,
        val durationMin: Int,
    )

    /**
     * The night's longest screen-off gap, between 21:00 yesterday and
     * 12:00 today. A suggestion only — Probe always confirms, never assumes.
     */
    fun suggestSleep(context: Context, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SleepSuggestion? {
        if (!hasPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val from = ZonedDateTime.of(LocalDateTime.of(date.minusDays(1), LocalTime.of(21, 0)), zone)
        val to = ZonedDateTime.of(LocalDateTime.of(date, LocalTime.of(12, 0)), zone)
        val events = usm.queryEvents(from.toInstant().toEpochMilli(), to.toInstant().toEpochMilli())
            ?: return null

        var lastOff = -1L
        var bestOff = -1L
        var bestOn = -1L
        var bestGap = 0L
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> lastOff = e.timeStamp
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (lastOff > 0) {
                        val gap = e.timeStamp - lastOff
                        if (gap > bestGap) {
                            bestGap = gap; bestOff = lastOff; bestOn = e.timeStamp
                        }
                        lastOff = -1L
                    }
                }
            }
        }
        // A night that never woke before noon: gap runs to the query edge.
        if (lastOff > 0) {
            val gap = to.toInstant().toEpochMilli() - lastOff
            if (gap > bestGap) {
                bestGap = gap; bestOff = lastOff; bestOn = to.toInstant().toEpochMilli()
            }
        }
        if (bestGap < 90 * 60_000L) return null // under 90 min is not a night

        val bed = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(bestOff), zone)
        val noon = ZonedDateTime.of(LocalDateTime.of(date.minusDays(1), LocalTime.NOON), zone)
        val bedMin = (java.time.Duration.between(noon, bed).toMinutes())
            .coerceIn(0, 1439).toInt()
        return SleepSuggestion(
            bedMinutesAfterNoon = bedMin,
            durationMin = (bestGap / 60_000L).toInt().coerceAtMost(16 * 60),
        )
    }

    /** Foreground screen time since local midnight, in minutes. */
    fun screenTimeTodayMin(context: Context, now: ZonedDateTime = ZonedDateTime.now()): Int? {
        if (!hasPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val midnight = now.toLocalDate().atStartOfDay(now.zone)
        val events = usm.queryEvents(midnight.toInstant().toEpochMilli(), now.toInstant().toEpochMilli())
            ?: return null
        var total = 0L
        val resumedAt = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[e.packageName + e.className] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = resumedAt.remove(e.packageName + e.className)
                    if (start != null) total += (e.timeStamp - start).coerceAtLeast(0)
                }
            }
        }
        // Anything still resumed counts up to now.
        for (start in resumedAt.values) total += (now.toInstant().toEpochMilli() - start).coerceAtLeast(0)
        return (total / 60_000L).toInt()
    }

    /** Under budget? Null when unknowable (no permission). */
    fun underBudget(context: Context, budgetMin: Int, now: ZonedDateTime = ZonedDateTime.now()): Boolean? =
        screenTimeTodayMin(context, now)?.let { it <= budgetMin }
}
