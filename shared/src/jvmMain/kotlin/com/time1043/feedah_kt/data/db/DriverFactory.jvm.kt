package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DriverFactory(private val filePath: String = defaultDbPath()) {
    actual fun createDriver(): SqlDriver {
        val file = File(filePath)
        val existed = file.exists()
        file.parentFile?.mkdirs()
        // JdbcSqliteDriver does not manage the schema; create it on a fresh file.
        val driver = JdbcSqliteDriver("jdbc:sqlite:$filePath")
        if (!existed) FeedahDatabase.Schema.create(driver)
        return driver
    }

    actual fun deleteDatabase() {
        for (suffix in listOf("", "-wal", "-shm")) File(filePath + suffix).delete()
    }

    companion object {
        fun defaultDbPath(): String {
            val home = System.getProperty("user.home") ?: "."
            return File(home, ".feedah/$DB_NAME").absolutePath
        }
    }
}
