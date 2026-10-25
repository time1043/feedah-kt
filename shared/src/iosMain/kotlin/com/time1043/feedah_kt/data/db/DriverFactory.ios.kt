@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.URLByAppendingPathComponent

actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        // NativeSqliteDriver places the file in the app's Documents directory
        // and owns schema creation itself.
        NativeSqliteDriver(FeedahDatabase.Schema, DB_NAME)

    actual fun deleteDatabase() {
        val documents = NSFileManager.defaultManager
            .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL ?: return
        for (suffix in listOf("", "-wal", "-shm")) {
            val path = documents.URLByAppendingPathComponent(DB_NAME + suffix)?.path ?: continue
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }
}
