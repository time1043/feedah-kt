package com.time1043.feedah_kt.data.db

import com.time1043.feedah_kt.logic.nowMs
import com.time1043.feedah_kt.logic.todayLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LiveUsage(val appMs: Long, val feedMs: Long)

/**
 * Tracks two daily time metrics while the app is in the foreground:
 * total app time and time spent on the feed screen. Seconds are accumulated
 * in memory and flushed to daily_stat on a fixed interval, on app state
 * changes, and on feed settle events, so a crash loses at most one interval.
 * Sub-second remainders stay in memory (floor on flush), like the original.
 */
object UsageTracker {
    /** Injectable for tests; production uses the wall clock. */
    internal var clock: () -> Long = { nowMs() }

    private var repo: FeedahRepo? = null
    private var appStartedAt: Long? = null
    private var feedStartedAt: Long? = null
    private var pendingAppMs = 0L
    private var pendingFeedMs = 0L
    private var tickerJob: Job? = null
    private var flushing = false

    fun bind(repo: FeedahRepo) {
        this.repo = repo
    }

    private fun tick(now: Long = clock()) {
        appStartedAt?.let { pendingAppMs += now - it }
        if (appStartedAt != null) appStartedAt = now
        feedStartedAt?.let { pendingFeedMs += now - it }
        if (feedStartedAt != null) feedStartedAt = now
    }

    fun flushUsage() {
        if (flushing) return
        flushing = true
        try {
            tick()
            val feedSeconds = pendingFeedMs / 1000
            val appSeconds = pendingAppMs / 1000
            pendingFeedMs -= feedSeconds * 1000
            pendingAppMs -= appSeconds * 1000
            if (feedSeconds > 0 || appSeconds > 0) {
                repo?.addDailyTime(todayLocalDate(), feedSeconds, appSeconds)
            }
        } finally {
            flushing = false
        }
    }

    fun startAppUsage(scope: CoroutineScope) {
        if (appStartedAt == null) appStartedAt = clock()
        if (tickerJob == null) {
            tickerJob = scope.launch {
                while (isActive) {
                    delay(10_000)
                    flushUsage()
                }
            }
        }
    }

    fun pauseAppUsage() {
        tick()
        appStartedAt = null
        tickerJob?.cancel()
        tickerJob = null
    }

    fun startFeedUsage() {
        if (feedStartedAt == null) feedStartedAt = clock()
    }

    fun pauseFeedUsage() {
        tick()
        feedStartedAt = null
    }

    /** Current totals including unflushed in-memory time, for live displays. */
    fun getLiveUsage(): LiveUsage {
        val now = clock()
        return LiveUsage(
            appMs = pendingAppMs + (appStartedAt?.let { now - it } ?: 0),
            feedMs = pendingFeedMs + (feedStartedAt?.let { now - it } ?: 0),
        )
    }

    /** Drops in-memory pending time (used after a full data wipe). */
    fun resetUsage() {
        pendingAppMs = 0
        pendingFeedMs = 0
    }

    /** Test-only: clears all state. */
    internal fun resetForTest() {
        appStartedAt = null
        feedStartedAt = null
        pendingAppMs = 0
        pendingFeedMs = 0
        tickerJob = null
        flushing = false
        clock = { nowMs() }
    }
}
