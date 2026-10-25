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
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manual dependency graph filled once at startup: the platform shell provides
 * the driver factory and TTS engine; init opens + seeds the database, then
 * exposes the repo, settings store and usage tracker. Mirrors the original
 * provider stack (SettingsProvider > getDb > usage).
 */
object AppGraph {
    /** App-lifetime scope for background work (usage ticker, fire-and-forget IO). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Bumped whenever the data layer is rebuilt (Clear all data) so the UI remounts. */
    val dataVersion = MutableStateFlow(0)

    @Volatile private var driverFactory: DriverFactory? = null

    @Volatile var tts: TtsPlayer? = null
        private set

    @Volatile var repo: FeedahRepo? = null
        private set

    @Volatile var settings: SettingsStore? = null
        private set

    val ready: Boolean get() = repo != null && settings != null

    suspend fun init(factory: DriverFactory, tts: TtsPlayer) {
        if (ready) return
        this.driverFactory = factory
        this.tts = tts
        DatabaseProvider.init(factory)
        val db = DatabaseProvider.require()
        val repo = FeedahRepo(db)
        this.repo = repo
        UsageTracker.bind(repo)
        this.settings = SettingsStore(db).also { it.load() }
    }

    /**
     * Erases everything (progress, flags, stats, settings), re-opens the
     * database and re-seeds. The UI remounts via [dataVersion]. The original
     * also wiped the cloud copy first — cloud sync is out of scope here.
     */
    suspend fun resetData() {
        val factory = driverFactory ?: return
        UsageTracker.pauseAppUsage()
        UsageTracker.resetUsage()
        DatabaseProvider.reset(factory)
        repo = null
        settings = null
        init(factory, tts ?: return)
        UsageTracker.startAppUsage(appScope)
        dataVersion.value += 1
    }
}
