package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.db.SqlDriver

internal const val DB_NAME = "feedah.db"

/** Builds the platform SQLite driver; each target owns its file location. */
expect class DriverFactory {
    fun createDriver(): SqlDriver

    /** Deletes the database file (and WAL/SHM sidecars) — the "Clear all data" flow. */
    fun deleteDatabase()
}
