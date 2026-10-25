package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStoreTest {
    private fun newDb(): FeedahDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FeedahDatabase.Schema.create(driver)
        val db = FeedahDatabase(driver)
        seedBuckets(db, listOf(SeedFile("t1", listOf(SeedWord(1, "w")))))
        return db
    }

    @Test
    fun defaultsBeforeAnythingStored() {
        val store = SettingsStore(newDb())
        store.load()
        assertTrue(store.ready.value)
        assertEquals(DEFAULT_SETTINGS, store.settings.value)
    }

    @Test
    fun updateWritesOneJsonRowPerChangedKey() {
        val db = newDb()
        val store = SettingsStore(db)
        store.load()

        store.update(store.settings.value.copy(theme = ThemeMode.dark, autoPronounce = false))

        val rows = db.feedahQueries.selectAllMeta().executeAsList()
        assertEquals("\"dark\"", rows.first { it.key == "theme" }.value_) // JSON-encoded, like the original
        assertEquals("false", rows.first { it.key == "autoPronounce" }.value_)

        val store2 = SettingsStore(db)
        store2.load()
        assertEquals(ThemeMode.dark, store2.settings.value.theme)
        assertFalse(store2.settings.value.autoPronounce)
    }

    @Test
    fun corruptValuesFallBackToDefaults() {
        val db = newDb()
        db.feedahQueries.upsertMeta("theme", "{{{not json", 0)
        db.feedahQueries.upsertMeta("autoPronounce", "not-a-bool", 0)
        db.feedahQueries.upsertMeta("speechRate", "\"turbo\"", 0) // valid JSON, bad enum

        val store = SettingsStore(db)
        store.load()

        assertEquals(ThemeMode.system, store.settings.value.theme)
        assertTrue(store.settings.value.autoPronounce)
        assertEquals(SpeechRate.normal, store.settings.value.speechRate)
    }

    @Test
    fun reloadRereadsFromDatabase() {
        val db = newDb()
        val store = SettingsStore(db)
        store.load()
        store.update(store.settings.value.copy(activeBucketId = "700"))

        val fresh = SettingsStore(db)
        fresh.load()
        assertEquals("700", fresh.settings.value.activeBucketId)
    }
}
