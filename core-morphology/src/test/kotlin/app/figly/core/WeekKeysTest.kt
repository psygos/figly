package app.figly.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekKeysTest {

    @Test
    fun `iso week keys are week-based years`() {
        // 2026-12-28 is a Monday in ISO week 53 of 2026.
        assertEquals("2026-W53", WeekKeys.isoWeekKey(LocalDate.of(2026, 12, 28)))
        // 2027-01-04 begins ISO week 1 of 2027 — W01 follows W53 cleanly.
        assertEquals("2027-W01", WeekKeys.isoWeekKey(LocalDate.of(2027, 1, 4)))
        // And the Jan days inside 2026-W53 still belong to it.
        assertEquals("2026-W53", WeekKeys.isoWeekKey(LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun `mondays resolve from keys`() {
        assertEquals(LocalDate.of(2026, 6, 8), WeekKeys.mondayOf("2026-W24"))
        assertEquals(LocalDate.of(2026, 12, 28), WeekKeys.mondayOf("2026-W53"))
        assertEquals(LocalDate.of(2027, 1, 4), WeekKeys.mondayOf("2027-W01"))
    }

    @Test
    fun `labels read like the plate`() {
        assertEquals("FIG-2026-W24", WeekKeys.figId("2026-W24"))
        assertEquals(24, WeekKeys.plateNumber("2026-W24"))
        assertEquals("08–14 JUN 2026", WeekKeys.dateRangeLabel("2026-W24"))
        assertEquals("28 DEC – 03 JAN 2027", WeekKeys.dateRangeLabel("2026-W53"))
    }
}
