package com.time1043.feedah_kt.logic

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val MONTHS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/** Formats seconds as "47m" or "1h 12m". */
fun formatMinutes(totalSeconds: Long): String {
    val minutes = Math.round(totalSeconds / 60.0)
    if (minutes < 60) return "${minutes}m"
    return "${minutes / 60}h ${minutes % 60}m"
}

/** Formats seconds as a live clock "h:mm:ss". */
fun formatClock(totalSeconds: Long): String {
    val total = maxOf(0, totalSeconds)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/** Formats a YYYY-MM-DD day as a short label like "Wed, Sep 3". */
fun formatDayLabel(day: String): String {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    // WEEKDAYS is Sunday-indexed; DayOfWeek enum is ISO-ordered (ordinal 0 = Monday).
    val weekday = WEEKDAYS[(date.dayOfWeek.ordinal + 1) % 7]
    return "$weekday, ${MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}"
}

/** Formats a YYYY-MM-DD day as "2026 Oct 7". */
fun formatYearMonthDay(day: String): String {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    return "${date.year} ${MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}"
}

/** Formats an epoch millisecond timestamp (local timezone) as "2026 Oct 7". */
fun formatYearMonthDay(timestamp: Long, tz: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz).date
    return "${date.year} ${MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}"
}
