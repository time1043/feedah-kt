package com.time1043.feedah_kt.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun formatMinutesUnderAnHour() {
        assertEquals("0m", formatMinutes(0))
        assertEquals("48m", formatMinutes(47 * 60 + 30)) // rounds
        assertEquals("59m", formatMinutes(59 * 60 + 29))
    }

    @Test
    fun formatMinutesOverAnHour() {
        assertEquals("1h 12m", formatMinutes(3600 + 12 * 60))
        assertEquals("2h 0m", formatMinutes(2 * 3600))
    }

    @Test
    fun formatClockPadsMinutesAndSeconds() {
        assertEquals("0:00:00", formatClock(0))
        assertEquals("0:00:59", formatClock(59))
        assertEquals("0:01:01", formatClock(61))
        assertEquals("1:01:01", formatClock(3661))
        assertEquals("12:00:00", formatClock(12 * 3600))
    }

    @Test
    fun formatClockClampsNegatives() {
        assertEquals("0:00:00", formatClock(-5))
    }

    @Test
    fun formatDayLabelKnownWeekdays() {
        // 2000-01-01 was a Saturday; 2024-03-01 was a Friday.
        assertEquals("Sat, Jan 1", formatDayLabel("2000-01-01"))
        assertEquals("Fri, Mar 1", formatDayLabel("2024-03-01"))
    }

    @Test
    fun formatDayLabelFallsBackToRawInput() {
        assertEquals("not-a-day", formatDayLabel("not-a-day"))
    }

    @Test
    fun formatYearMonthDayFromString() {
        assertEquals("2026 Oct 7", formatYearMonthDay("2026-10-07"))
        assertEquals("2026 Jan 31", formatYearMonthDay("2026-01-31"))
    }

    @Test
    fun formatYearMonthDayFromTimestamp() {
        // 2026-10-07T10:00Z is 18:00 local in UTC+8 — same local day.
        val ts = kotlinx.datetime.Instant.parse("2026-10-07T10:00:00Z").toEpochMilliseconds()
        val label = formatYearMonthDay(ts, kotlinx.datetime.TimeZone.of("+08:00"))
        assertEquals("2026 Oct 7", label)
    }

    @Test
    fun formatYearMonthDayFallsBackToRawInput() {
        assertEquals("oops", formatYearMonthDay("oops"))
    }
}
