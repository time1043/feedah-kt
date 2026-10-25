package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Holds the single app database. Shells call [init] once at startup with a
 * platform [DriverFactory]; seeding runs during init (see [seedBuckets]).
 * Mirrors the failed-open retry contract of the original getDb(): a failure
 * leaves the provider uninitialized so the next call retries.
 */
object DatabaseProvider {
    @Volatile private var driver: SqlDriver? = null
    @Volatile private var database: FeedahDatabase? = null

    val isReady: Boolean get() = database != null

    fun init(factory: DriverFactory) {
        if (database != null) return
        try {
            val newDriver = factory.createDriver()
            driver = newDriver
            database = FeedahDatabase(newDriver)
        } catch (error: Throwable) {
            driver = null
            database = null
            throw error
        }
    }

    fun require(): FeedahDatabase = database ?: error("database not initialized")

    /** Erases everything (progress, flags, stats, settings) and re-opens fresh. */
    fun reset(factory: DriverFactory) {
        driver?.close()
        driver = null
        database = null
        factory.deleteDatabase()
    }
}
