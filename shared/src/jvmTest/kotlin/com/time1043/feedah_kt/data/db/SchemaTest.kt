package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaTest {
    @Test
    fun createsSchemaAndRunsQueries() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FeedahDatabase.Schema.create(driver)
        val db = FeedahDatabase(driver)

        db.feedahQueries.upsertBucket("2050", 5)
        assertEquals(5, db.feedahQueries.selectWordCount("2050").executeAsOne())

        db.feedahQueries.insertBucketProgressIfAbsent("2050", 0)
        val progress = db.feedahQueries.selectProgress("2050").executeAsOne()
        assertEquals(1, progress.round)
        assertEquals(0, progress.pointer)
    }
}
