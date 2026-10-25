package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.time1043.feedah_kt.logic.dayBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepoStatsTest {
    private fun newRepo(): FeedahRepo {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FeedahDatabase.Schema.create(driver)
        val db = FeedahDatabase(driver)
        seedBuckets(
            db,
            listOf(
                SeedFile("t1", (1..10).map { SeedWord(it.toLong(), "w$it") }),
                SeedFile("t2", (1..5).map { SeedWord(it.toLong(), "u$it") }),
            ),
        )
        return FeedahRepo(db)
    }

    @Test
    fun addDailyTimeAccumulatesIncrements() {
        val repo = newRepo()
        repo.addDailyTime("2026-10-24", 30, 60)
        repo.addDailyTime("2026-10-24", 12, 5)
        repo.addDailyTime("2026-10-25", 40, 0)

        val day1 = repo.getDailyStat("2026-10-24")!!
        assertEquals(42, day1.feedSeconds)
        assertEquals(65, day1.appSeconds)
        assertEquals(40, repo.getDailyStat("2026-10-25")!!.feedSeconds)
        assertEquals(listOf("2026-10-24", "2026-10-25"), repo.listDailyStats().map { it.day })
    }

    @Test
    fun wordsCompletedOnRebuildsFromHighWaterDeltas() {
        val repo = newRepo()
        val q = repo.db.feedahQueries
        // Bucket t1 (10 words): day1 reaches globalPosition 5; day2 advances to 12
        // → wraps into round 2 positions 1..2. Bucket t2: day2 only.
        q.upsertDailyPointer("2026-10-01", "t1", 5, 0)
        q.upsertDailyPointer("2026-10-02", "t1", 12, 0)
        q.upsertDailyPointer("2026-10-02", "t2", 2, 0)

        val day1 = repo.getWordsCompletedOn("2026-10-01")
        assertEquals(listOf(1, 2, 3, 4, 5), day1.filter { it.bucketId == "t1" }.map { it.position })

        // Day2 t1: gains 6..10 then wrap 1..2 → positions {6,7,8,9,10,1,2};
        // plus t2's {1,2} (u-words).
        val day2 = repo.getWordsCompletedOn("2026-10-02")
        assertEquals(
            listOf(1, 2, 6, 7, 8, 9, 10),
            day2.filter { it.bucketId == "t1" }.map { it.position },
        )
        assertEquals(listOf(1, 2), day2.filter { it.bucketId == "t2" }.map { it.position })
    }

    @Test
    fun wordsCompletedOnIgnoresPointerRegressions() {
        val repo = newRepo()
        val q = repo.db.feedahQueries
        q.upsertDailyPointer("2026-10-01", "t1", 8, 0)
        // A lower snapshot the next day gains nothing (already covered before).
        q.upsertDailyPointer("2026-10-02", "t1", 4, 0)

        assertEquals(emptyList(), repo.getWordsCompletedOn("2026-10-02"))
        assertEquals((1..8).toList(), repo.getWordsCompletedOn("2026-10-01").map { it.position })
    }

    @Test
    fun recoverRoundStartedAtPrefersEarliestReachedWord() {
        val repo = newRepo()
        repo.advancePointer("t1", 3)
        val reachedAt = repo.db.feedahQueries
            .selectRoundWord("t1", 1, 3).executeAsOne().reached_at

        assertEquals(reachedAt, repo.recoverRoundStartedAt("t1", round = 1, wordCount = 10))
    }

    @Test
    fun recoverRoundStartedAtFallsBackToPointerSnapshotDay() {
        val repo = newRepo()
        // No round_word rows for round 2; a daily_pointer at gp >= 11 exists.
        repo.db.feedahQueries.upsertDailyPointer("2026-10-05", "t1", 11, 0)

        val recovered = repo.recoverRoundStartedAt("t1", round = 2, wordCount = 10)

        assertEquals(dayBounds("2026-10-05").start, recovered)
    }

    @Test
    fun recoverRoundStartedAtReturnsZeroWithoutEvidence() {
        val repo = newRepo()
        assertEquals(0, repo.recoverRoundStartedAt("t1", round = 1, wordCount = 10))
    }

    @Test
    fun reviewCursorIsMonotonicAndClearable() {
        val repo = newRepo()
        repo.saveReviewCursor("day:2026-10-01", 5)
        repo.saveReviewCursor("day:2026-10-01", 3) // swiping back never lowers
        assertEquals(5, repo.getReviewCursor("day:2026-10-01"))

        repo.saveReviewCursor("day:2026-10-01", 0) // ignored
        assertEquals(5, repo.getReviewCursor("day:2026-10-01"))

        repo.clearReviewCursor("day:2026-10-01")
        assertEquals(0, repo.getReviewCursor("day:2026-10-01"))
    }

    @Test
    fun listDailyPointersOrdersByDay() {
        val repo = newRepo()
        val q = repo.db.feedahQueries
        q.upsertDailyPointer("2026-10-02", "t1", 12, 0)
        q.upsertDailyPointer("2026-10-01", "t1", 5, 0)
        q.upsertDailyPointer("2026-10-01", "t2", 3, 0)

        assertEquals(
            listOf("2026-10-01", "2026-10-01", "2026-10-02"),
            repo.listDailyPointers().map { it.day },
        )
        assertTrue(repo.listDailyPointers().all { it.globalPosition in 3..12 })
        assertEquals(2, repo.getDailyPointersForBucket("t1").size)
    }
}
