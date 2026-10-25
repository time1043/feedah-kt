package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.time1043.feedah_kt.logic.todayLocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageTrackerTest {
    private var now = 1_000_000L
    private lateinit var repo: FeedahRepo

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FeedahDatabase.Schema.create(driver)
        val db = FeedahDatabase(driver)
        seedBuckets(db, listOf(SeedFile("t1", listOf(SeedWord(1, "w")))))
        repo = FeedahRepo(db)
        UsageTracker.resetForTest()
        UsageTracker.bind(repo)
        UsageTracker.clock = { now }
    }

    @AfterTest
    fun tearDown() {
        UsageTracker.resetForTest()
    }

    @Test
    fun appTimeFlushesFlooredSeconds() {
        UsageTracker.startAppUsage(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        now += 25_400

        UsageTracker.pauseAppUsage()
        UsageTracker.flushUsage()

        val stat = repo.getDailyStat(todayLocalDate())!!
        assertEquals(25, stat.appSeconds)
        assertEquals(0, stat.feedSeconds)
        // 400ms remainder stays in memory.
        assertEquals(400, UsageTracker.getLiveUsage().appMs)
    }

    @Test
    fun remaindersRollIntoTheNextFlush() {
        UsageTracker.startAppUsage(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        now += 25_400
        UsageTracker.pauseAppUsage()
        UsageTracker.flushUsage() // 25s flushed, 400ms kept

        UsageTracker.startAppUsage(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        now += 700
        UsageTracker.pauseAppUsage()
        UsageTracker.flushUsage() // 400 + 700 = 1100ms → 1s

        assertEquals(26, repo.getDailyStat(todayLocalDate())!!.appSeconds)
    }

    @Test
    fun feedAndAppTimeTrackIndependently() {
        UsageTracker.startAppUsage(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        UsageTracker.startFeedUsage()
        now += 10_000
        UsageTracker.pauseFeedUsage() // feed: 10s
        now += 5_000
        UsageTracker.pauseAppUsage() // app: 15s
        UsageTracker.flushUsage()

        val stat = repo.getDailyStat(todayLocalDate())!!
        assertEquals(15, stat.appSeconds)
        assertEquals(10, stat.feedSeconds)
    }

    @Test
    fun pausedTrackerFlushesNothingNew() {
        UsageTracker.startAppUsage(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        UsageTracker.pauseAppUsage()
        now += 60_000
        UsageTracker.flushUsage()

        assertEquals(null, repo.getDailyStat(todayLocalDate()))
    }

    @Test
    fun getLiveUsageIncludesRunningSegments() {
        UsageTracker.startAppUsage(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        UsageTracker.startFeedUsage()
        now += 3_500

        val live = UsageTracker.getLiveUsage()
        assertEquals(3_500, live.appMs)
        assertEquals(3_500, live.feedMs)
    }

    @Test
    fun resetUsageDropsPendingTime() {
        UsageTracker.startAppUsage(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        now += 30_000
        UsageTracker.pauseAppUsage()
        UsageTracker.flushUsage()

        UsageTracker.resetUsage()
        assertEquals(0, UsageTracker.getLiveUsage().appMs)
    }
}
