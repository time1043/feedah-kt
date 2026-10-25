package com.time1043.feedah_kt

import com.time1043.feedah_kt.data.db.DatabaseProvider
import com.time1043.feedah_kt.data.db.DriverFactory
import com.time1043.feedah_kt.data.db.FeedahRepo
import com.time1043.feedah_kt.data.db.SettingsStore
import com.time1043.feedah_kt.data.db.UsageTracker
import com.time1043.feedah_kt.platform.TtsPlayer
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph filled once at startup: the platform shell provides
 * the driver factory and TTS engine; init opens + seeds the database, then
 * exposes the repo, settings store and usage tracker. Mirrors the original
 * provider stack (SettingsProvider > getDb > usage).
 */
object AppGraph {
    /** App-lifetime scope for background work (usage ticker, fire-and-forget IO). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile var tts: TtsPlayer? = null
        private set

    @Volatile var repo: FeedahRepo? = null
        private set

    @Volatile var settings: SettingsStore? = null
        private set

    val ready: Boolean get() = repo != null && settings != null

    suspend fun init(driverFactory: DriverFactory, tts: TtsPlayer) {
        if (ready) return
        this.tts = tts
        DatabaseProvider.init(driverFactory)
        val db = DatabaseProvider.require()
        val repo = FeedahRepo(db)
        this.repo = repo
        UsageTracker.bind(repo)
        this.settings = SettingsStore(db).also { it.load() }
    }

    /** Erases everything and re-opens fresh (the "Clear all data" flow). */
    fun resetData(driverFactory: DriverFactory) {
        UsageTracker.pauseAppUsage()
        UsageTracker.resetUsage()
        DatabaseProvider.reset(driverFactory)
        repo = null
        settings = null
    }
}
