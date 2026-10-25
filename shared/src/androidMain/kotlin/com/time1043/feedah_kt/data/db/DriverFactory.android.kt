package com.time1043.feedah_kt.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        // AndroidSqliteDriver owns schema creation and migrations itself.
        AndroidSqliteDriver(FeedahDatabase.Schema, context, DB_NAME)

    actual fun deleteDatabase() {
        context.deleteDatabase(DB_NAME)
    }
}
