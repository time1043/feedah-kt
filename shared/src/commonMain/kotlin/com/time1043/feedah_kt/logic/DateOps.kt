package com.time1043.feedah_kt.logic

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private const val DAY_MS = 86_400_000L

/** Wall-clock now in epoch milliseconds (Date.now() in the TS original). */
fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Local-timezone calendar date as YYYY-MM-DD. */
fun todayLocalDate(now: Instant = Clock.System.now(), tz: TimeZone = TimeZone.currentSystemDefault()): String =
    now.toLocalDateTime(tz).date.toString()

/** Local-timezone epoch bounds of a YYYY-MM-DD day. */
fun dayBounds(day: String, tz: TimeZone = TimeZone.currentSystemDefault()): DayBounds {
    val start = LocalDate.parse(day).atStartOfDayIn(tz).toEpochMilliseconds()
    return DayBounds(start = start, end = start + DAY_MS)
}

data class DayBounds(val start: Long, val end: Long)

/**
 * Number of calendar days spanned between two timestamps, inclusive of both the start and end day.
 * e.g. same day = 1d, next day = 2d.
 */
fun calendarDaysBetween(
    startTs: Long,
    endTs: Long,
    tz: TimeZone = TimeZone.currentSystemDefault(),
): Int {
    if (startTs <= 0 || endTs <= 0) return 1
    val startDay = Instant.fromEpochMilliseconds(startTs).toLocalDateTime(tz).date
    val endDay = Instant.fromEpochMilliseconds(endTs).toLocalDateTime(tz).date
    // Calendar-day arithmetic (epochDays), not raw millisecond subtraction — DST-safe.
    val diffDays = (endDay.toEpochDays() - startDay.toEpochDays()).toInt()
    return maxOf(1, diffDays + 1)
}
