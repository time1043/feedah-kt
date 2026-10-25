package com.time1043.feedah_kt.logic

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DateOpsTest {
    private val utc = TimeZone.UTC
    private val plus8 = TimeZone.of("+08:00")

    @Test
    fun todayLocalDateFormatsIso() {
        assertEquals("2026-10-25", todayLocalDate(Instant.parse("2026-10-25T13:45:00Z"), utc))
    }

    @Test
    fun todayLocalDateRollsOverAtLocalMidnight() {
        // 16:00Z is already next day in UTC+8.
        assertEquals("2026-10-26", todayLocalDate(Instant.parse("2026-10-25T16:00:00Z"), plus8))
        assertEquals("2026-10-25", todayLocalDate(Instant.parse("2026-10-25T15:59:59Z"), plus8))
    }

    @Test
    fun dayBoundsStartsAtLocalMidnight() {
        val bounds = dayBounds("2026-10-25", plus8)
        assertEquals(Instant.parse("2026-10-24T16:00:00Z").toEpochMilliseconds(), bounds.start)
        assertEquals(bounds.start + 86_400_000L, bounds.end)
    }

    @Test
    fun calendarDaysBetweenSameInstantIsOne() {
        val ts = Instant.parse("2026-10-25T08:00:00Z").toEpochMilliseconds()
        assertEquals(1, calendarDaysBetween(ts, ts, utc))
    }

    @Test
    fun calendarDaysBetweenNextDayIsTwo() {
        val start = Instant.parse("2026-10-25T08:00:00Z").toEpochMilliseconds()
        val end = Instant.parse("2026-10-26T20:00:00Z").toEpochMilliseconds()
        assertEquals(2, calendarDaysBetween(start, end, utc))
    }

    @Test
    fun calendarDaysBetweenSpansDstShiftWithoutDrift() {
        // US Eastern DST ends 2026-11-01; midnight epoch deltas around it are 23h/25h.
        val eastern = TimeZone.of("America/New_York")
        val start = Instant.parse("2026-10-31T12:00:00Z").toEpochMilliseconds()
        val end = Instant.parse("2026-11-02T12:00:00Z").toEpochMilliseconds()
        assertEquals(3, calendarDaysBetween(start, end, eastern))
    }

    @Test
    fun calendarDaysBetweenNonPositiveReturnsOne() {
        assertEquals(1, calendarDaysBetween(0, 123L, utc))
        assertEquals(1, calendarDaysBetween(123L, -1L, utc))
    }
}
