package com.time1043.feedah_kt.logic

/**
 * Row inputs for [computeDailyUsage]. Kept as standalone data classes so the
 * pure aggregation logic has no dependency on the data layer; the repo maps
 * its query rows onto these.
 */
data class DailyStatRow(val day: String, val feedSeconds: Long, val appSeconds: Long)

data class DailyPointerRow(val day: String, val bucketId: String, val globalPosition: Long)

data class DailyUsage(
    var words: Long,
    var feedSeconds: Long,
    var appSeconds: Long,
)

/**
 * Aggregates daily activity. Word counts come from the difference of
 * consecutive daily high-water pointer snapshots (never from per-card
 * events), so re-viewing earlier cards never inflates the numbers.
 * A pointer row holds the end-of-day value; each row's gain over the
 * previous recorded value is credited to its own day.
 */
fun computeDailyUsage(
    stats: List<DailyStatRow>,
    pointers: List<DailyPointerRow>,
): Map<String, DailyUsage> {
    val usage = LinkedHashMap<String, DailyUsage>()
    fun entry(day: String): DailyUsage = usage.getOrPut(day) { DailyUsage(0, 0, 0) }

    for (stat in stats) {
        val day = entry(stat.day)
        day.feedSeconds += stat.feedSeconds
        day.appSeconds += stat.appSeconds
    }

    val byBucket = pointers.groupBy { it.bucketId }
    for (rows in byBucket.values) {
        val sorted = rows.sortedBy { it.day } // YYYY-MM-DD sorts chronologically
        var previous = 0L
        for (row in sorted) {
            entry(row.day).words += maxOf(0L, row.globalPosition - previous)
            previous = row.globalPosition
        }
    }

    return usage
}
