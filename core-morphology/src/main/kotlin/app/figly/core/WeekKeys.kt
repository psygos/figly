package app.figly.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * ISO week identity. `FIG-2027-W01` follows `FIG-2026-W53` cleanly because
 * these are week-based years, not calendar years.
 */
object WeekKeys {

    /** "2026-W24" for the ISO week containing [date]. */
    fun isoWeekKey(date: LocalDate): String {
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "%04d-W%02d".format(Locale.ROOT, year, week)
    }

    /** "FIG-2026-W24" — the archived specimen id. */
    fun figId(isoWeekKey: String): String = "FIG-$isoWeekKey"

    /** Plate number is the week number: "PLATE 24". */
    fun plateNumber(isoWeekKey: String): Int = isoWeekKey.substringAfter("-W").toInt()

    /** Monday of the week. */
    fun monday(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun mondayOf(isoWeekKey: String): LocalDate {
        val year = isoWeekKey.substringBefore("-W").toInt()
        val week = isoWeekKey.substringAfter("-W").toInt()
        return LocalDate.of(year, 6, 15) // mid-year anchor, then set week fields
            .with(IsoFields.WEEK_BASED_YEAR, year.toLong())
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(DayOfWeek.MONDAY)
    }

    /** Day index within its week, Monday = 1 .. Sunday = 7. */
    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value

    /** "01–07 JUN 2026" or "29 DEC – 04 JAN 2026" when the week crosses months. */
    fun dateRangeLabel(isoWeekKey: String): String {
        val mon = mondayOf(isoWeekKey)
        val sun = mon.plusDays(6)
        val dd = DateTimeFormatter.ofPattern("dd", Locale.ROOT)
        val mmm = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ROOT)
        return if (mon.month == sun.month) {
            "${mon.format(dd)}–${sun.format(dd)} ${sun.format(mmm).uppercase(Locale.ROOT)}"
        } else {
            val dmmm = DateTimeFormatter.ofPattern("dd MMM", Locale.ROOT)
            "${mon.format(dmmm).uppercase(Locale.ROOT)} – ${sun.format(dmmm).uppercase(Locale.ROOT)} ${sun.format(DateTimeFormatter.ofPattern("yyyy"))}"
        }
    }

    val DAY_NAMES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
}
