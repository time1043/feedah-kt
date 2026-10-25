package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepoWriteTest {
    private fun newRepo(): FeedahRepo {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FeedahDatabase.Schema.create(driver)
        val db = FeedahDatabase(driver)
        seedBuckets(
            db,
            listOf(
                SeedFile(
                    "t1",
                    (1..10).map { SeedWord(it.toLong(), "w$it", forms = listOf("f$it")) },
                ),
            ),
        )
        return FeedahRepo(db)
    }

    @Test
    fun advancePointerMarksReachedAndWritesHighWater() {
        val repo = newRepo()

        val after = repo.advancePointer("t1", 3)

        assertEquals(3, after.pointer)
        assertEquals(Progress(1, 3, after.startedAt), repo.getProgress("t1"))
        val roundWord = repo.dbRoundWord("t1", round = 1, position = 3)
        assertNotNull(roundWord)
        assertEquals(1L, roundWord.reached)
        val pointer = repo.dbDailyPointer("t1")
        assertNotNull(pointer)
        assertEquals(3L, pointer.global_position) // round 1 → (1-1)*10+3
    }

    @Test
    fun advancePointerIsMonotonic() {
        val repo = newRepo()
        repo.advancePointer("t1", 5)

        val before = repo.getProgress("t1")
        val after = repo.advancePointer("t1", 2)

        assertEquals(before, after)
        assertEquals(5, repo.getProgress("t1").pointer)
    }

    @Test
    fun advancePointerFillsStartedAtOnce() {
        val repo = newRepo()
        val first = repo.advancePointer("t1", 1)
        assertTrue(first.startedAt > 0)

        // Reaching further keeps the original startedAt.
        val second = repo.advancePointer("t1", 2)
        assertEquals(first.startedAt, second.startedAt)
    }

    @Test
    fun advancePointerSnapshotsFlagAtSettleTime() {
        val repo = newRepo()
        repo.setFlag("t1", 4, true)
        repo.advancePointer("t1", 4)

        assertEquals(1L, repo.dbRoundWord("t1", 1, 4)!!.flagged)

        // A word flagged after being reached keeps its round_word flagged untouched.
        repo.setFlag("t1", 4, false)
        val row = repo.dbRoundWord("t1", 1, 4)!!
        assertEquals(1L, row.reached)
        assertEquals(0L, row.flagged)
    }

    @Test
    fun startNextRoundRequiresFullPointerAndIsIdempotent() {
        val repo = newRepo()
        repo.advancePointer("t1", 10)

        val next = repo.startNextRound("t1")

        assertEquals(Progress(2, 0, next.startedAt), repo.getProgress("t1"))
        val history = repo.dbRoundHistory("t1", 1)
        assertNotNull(history)

        // Duplicate settle on the round-end card cannot skip a round.
        repo.startNextRound("t1")
        repo.advancePointer("t1", 10)
        assertEquals(2, repo.getProgress("t1").round)
    }

    @Test
    fun startNextRoundBelowWordCountIsNoop() {
        val repo = newRepo()
        repo.advancePointer("t1", 3)

        val result = repo.startNextRound("t1")

        assertEquals(1, result.round)
        assertEquals(3, repo.getProgress("t1").pointer)
        assertNull(repo.dbRoundHistory("t1", 1))
    }

    @Test
    fun setFlagWithoutReachRemovesRowOnUnflag() {
        val repo = newRepo()
        repo.setFlag("t1", 5, true)
        assertEquals(1L, repo.dbRoundWord("t1", 1, 5)!!.flagged)
        assertEquals(0L, repo.dbRoundWord("t1", 1, 5)!!.reached)

        repo.setFlag("t1", 5, false)
        assertNull(repo.dbRoundWord("t1", 1, 5))
        assertEquals(false, repo.getWord("t1", 5)!!.flagged)
    }
}

/** Raw-row accessors for asserting storage state (jvmTest only). */
private fun FeedahRepo.dbRoundWord(bucketId: String, round: Int, position: Int) =
    db.feedahQueries.selectRoundWord(bucketId, round.toLong(), position.toLong()).executeAsOneOrNull()

private fun FeedahRepo.dbDailyPointer(bucketId: String) =
    db.feedahQueries.selectDailyPointers().executeAsList().firstOrNull { it.bucket_id == bucketId }

private fun FeedahRepo.dbRoundHistory(bucketId: String, round: Int) =
    db.feedahQueries.selectRoundHistory(bucketId).executeAsList().firstOrNull { it.round == round.toLong() }
