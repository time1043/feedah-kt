package com.time1043.feedah_kt.data.db

import com.time1043.feedah_kt.logic.nowMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class ThemeMode { system, light, dark }

enum class SpeechRate { slow, normal, fast }

/** How cards treat the meaning: hidden until tapped, shown until tapped, or always shown. */
enum class MeaningMode { hidden, shown, always }

data class Settings(
    val activeBucketId: String = DEFAULT_BUCKET_ID,
    val theme: ThemeMode = ThemeMode.system,
    val autoPronounce: Boolean = true,
    val speechRate: SpeechRate = SpeechRate.normal,
    val meaningMode: MeaningMode = MeaningMode.hidden,
    val progressBar: Boolean = false,
    val progressBarDrag: Boolean = true,
    val wordProgressBar: Boolean = true,
    /** Whether the words list shows the meaning column (default hidden). */
    val wordsMeaning: Boolean = false,
    val feedSearch: Boolean = true,
    val todayReadout: Boolean = true,
    val silentHintShown: Boolean = false,
)

val DEFAULT_SETTINGS: Settings = Settings()

/** Speech rate multiplier for TTS; 1.0 is the system default. */
val SPEECH_RATE_VALUE: Map<SpeechRate, Float> = mapOf(
    SpeechRate.slow to 0.9f,
    SpeechRate.normal to 1.15f,
    SpeechRate.fast to 1.4f,
)

private val json = Json

/**
 * Preferences persisted in the meta table as one JSON value per key — the same
 * storage shape as the original. The reminders and accountEmail keys of the TS
 * app are deliberately dropped (notifications and cloud sync are out of scope).
 */
class SettingsStore(internal val db: FeedahDatabase) {
    private val q = db.feedahQueries

    private val _settings = MutableStateFlow(DEFAULT_SETTINGS)
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private fun loadSettings(): Settings {
        val stored = q.selectAllMeta().executeAsList()
            .associate { it.key to it.value_ }
        fun storedString(key: String): String? = stored[key]?.let { raw ->
            // JSON.parse failure falls back to the raw value, like the original.
            runCatching { json.parseToJsonElement(raw).jsonPrimitive.content }.getOrDefault(raw)
        }
        fun storedBoolean(key: String, default: Boolean): Boolean {
            val raw = stored[key] ?: return default
            val parsed = runCatching { json.parseToJsonElement(raw).jsonPrimitive.booleanOrNull }.getOrNull()
            return parsed ?: default
        }

        val defaults = DEFAULT_SETTINGS
        return Settings(
            activeBucketId = storedString("activeBucketId") ?: defaults.activeBucketId,
            theme = storedString("theme")?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.theme,
            autoPronounce = storedBoolean("autoPronounce", defaults.autoPronounce),
            speechRate = storedString("speechRate")?.let { runCatching { SpeechRate.valueOf(it) }.getOrNull() }
                ?: defaults.speechRate,
            meaningMode = storedString("meaningMode")?.let { runCatching { MeaningMode.valueOf(it) }.getOrNull() }
                ?: defaults.meaningMode,
            progressBar = storedBoolean("progressBar", defaults.progressBar),
            progressBarDrag = storedBoolean("progressBarDrag", defaults.progressBarDrag),
            wordProgressBar = storedBoolean("wordProgressBar", defaults.wordProgressBar),
            wordsMeaning = storedBoolean("wordsMeaning", defaults.wordsMeaning),
            feedSearch = storedBoolean("feedSearch", defaults.feedSearch),
            todayReadout = storedBoolean("todayReadout", defaults.todayReadout),
            silentHintShown = storedBoolean("silentHintShown", defaults.silentHintShown),
        )
    }

    /** Reads stored settings once at startup; ready flips when done. */
    fun load() {
        _settings.value = runCatching { loadSettings() }.getOrDefault(DEFAULT_SETTINGS)
        _ready.value = true
    }

    /**
     * Optimistic update: the in-memory value changes immediately and every
     * changed key is written to meta as its own JSON value.
     */
    fun update(newSettings: Settings) {
        val old = _settings.value
        _settings.value = newSettings
        val now = nowMs()
        val writes = ArrayList<Pair<String, String>>()
        if (old.activeBucketId != newSettings.activeBucketId) {
            writes += "activeBucketId" to json.encodeToString(newSettings.activeBucketId)
        }
        if (old.theme != newSettings.theme) writes += "theme" to json.encodeToString(newSettings.theme)
        if (old.autoPronounce != newSettings.autoPronounce) {
            writes += "autoPronounce" to json.encodeToString(newSettings.autoPronounce)
        }
        if (old.speechRate != newSettings.speechRate) {
            writes += "speechRate" to json.encodeToString(newSettings.speechRate)
        }
        if (old.meaningMode != newSettings.meaningMode) {
            writes += "meaningMode" to json.encodeToString(newSettings.meaningMode)
        }
        if (old.progressBar != newSettings.progressBar) {
            writes += "progressBar" to json.encodeToString(newSettings.progressBar)
        }
        if (old.progressBarDrag != newSettings.progressBarDrag) {
            writes += "progressBarDrag" to json.encodeToString(newSettings.progressBarDrag)
        }
        if (old.wordProgressBar != newSettings.wordProgressBar) {
            writes += "wordProgressBar" to json.encodeToString(newSettings.wordProgressBar)
        }
        if (old.wordsMeaning != newSettings.wordsMeaning) {
            writes += "wordsMeaning" to json.encodeToString(newSettings.wordsMeaning)
        }
        if (old.feedSearch != newSettings.feedSearch) {
            writes += "feedSearch" to json.encodeToString(newSettings.feedSearch)
        }
        if (old.todayReadout != newSettings.todayReadout) {
            writes += "todayReadout" to json.encodeToString(newSettings.todayReadout)
        }
        if (old.silentHintShown != newSettings.silentHintShown) {
            writes += "silentHintShown" to json.encodeToString(newSettings.silentHintShown)
        }
        for ((key, value) in writes) {
            q.upsertMeta(key, value, now)
        }
    }

    /** Re-reads settings from the database (after a full data wipe). */
    fun reload() {
        _settings.value = DEFAULT_SETTINGS
        _settings.value = runCatching { loadSettings() }.getOrDefault(DEFAULT_SETTINGS)
        _ready.value = true
    }
}
