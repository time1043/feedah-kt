package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedTest {
    private fun newDb(): FeedahDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FeedahDatabase.Schema.create(driver)
        return FeedahDatabase(driver)
    }

    private fun file(name: String, words: List<String>) =
        SeedFile(name, words.mapIndexed { i, w -> SeedWord((i + 1).toLong(), w) })

    @Test
    fun seedsBucketsWordsAndProgress() {
        val db = newDb()
        val source = listOf(file("aaa", listOf("alpha", "beta", "gamma")))

        seedBuckets(db, source)

        assertEquals(3, db.feedahQueries.selectWordCount("aaa").executeAsOne())
        assertEquals(listOf("alpha", "beta", "gamma"), db.feedahQueries.selectWords("aaa").executeAsList().map { it.text })
        val progress = db.feedahQueries.selectProgress("aaa").executeAsOne()
        assertEquals(1, progress.round)
        assertEquals(0, progress.pointer)
    }

    @Test
    fun reseedWithUnchangedCountPreservesFlagsAndProgress() {
        val db = newDb()
        val source = listOf(file("aaa", listOf("alpha", "beta", "gamma")))
        seedBuckets(db, source)

        db.feedahQueries.updateWordFlag(flagged = 1, flagged_at = 42, bucket_id = "aaa", position = 2)
        db.feedahQueries.updateProgressPointer(pointer = 3, progress_updated_at = 7, bucket_id = "aaa")

        seedBuckets(db, source) // unchanged bundle → untouched data

        assertTrue(db.feedahQueries.selectWord("aaa", 2).executeAsOne().flagged != 0L)
        assertEquals(3, db.feedahQueries.selectProgress("aaa").executeAsOne().pointer)
    }

    @Test
    fun reseedWithChangedCountResetsFlagsButKeepsProgress() {
        val db = newDb()
        seedBuckets(db, listOf(file("aaa", listOf("alpha", "beta", "gamma"))))
        db.feedahQueries.updateWordFlag(flagged = 1, flagged_at = 42, bucket_id = "aaa", position = 2)
        db.feedahQueries.updateProgressPointer(pointer = 2, progress_updated_at = 7, bucket_id = "aaa")

        seedBuckets(db, listOf(file("aaa", listOf("alpha", "beta", "gamma", "delta"))))

        assertEquals(4, db.feedahQueries.selectWordCount("aaa").executeAsOne())
        assertTrue(db.feedahQueries.selectWord("aaa", 2).executeAsOne().flagged == 0L)
        assertEquals(2, db.feedahQueries.selectProgress("aaa").executeAsOne().pointer)
        assertEquals("delta", db.feedahQueries.selectWord("aaa", 4).executeAsOne().text)
    }
}
