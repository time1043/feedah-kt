package com.time1043.feedah_kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.time1043.feedah_kt.AppGraph
import com.time1043.feedah_kt.data.db.MeaningMode
import com.time1043.feedah_kt.data.db.SpeechRate
import com.time1043.feedah_kt.data.db.ThemeMode
import com.time1043.feedah_kt.data.db.UsageTracker
import com.time1043.feedah_kt.logic.formatClock
import com.time1043.feedah_kt.logic.todayLocalDate
import com.time1043.feedah_kt.ui.components.ChevronDownIcon
import com.time1043.feedah_kt.ui.components.Screen
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Radius
import com.time1043.feedah_kt.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Mirrors the original app.json version the TS build read from Constants. */
private const val APP_VERSION = "0.0.2"

private val THEME_OPTIONS =
    listOf(
        ThemeMode.system to "System",
        ThemeMode.light to "Light",
        ThemeMode.dark to "Dark",
    )

private val RATE_OPTIONS =
    listOf(
        SpeechRate.slow to "Slow",
        SpeechRate.normal to "Normal",
        SpeechRate.fast to "Fast",
    )

private val MEANING_OPTIONS =
    listOf(
        MeaningMode.hidden to "Tap to show",
        MeaningMode.shown to "Tap to hide",
        MeaningMode.always to "Always shown",
    )

private data class LiveSample(val appSeconds: Long, val feedSeconds: Long)

@Composable
fun SettingsScreen(nav: NavHostController) {
    val colors = LocalFeedahColors.current
    val settingsStore = AppGraph.settings!!
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var todayAppSeconds by remember { mutableStateOf(0L) }
    var todayFeedSeconds by remember { mutableStateOf(0L) }
    var live by remember { mutableStateOf(LiveSample(0, 0)) }
    var soundHintOpen by remember { mutableStateOf(false) }
    var clearConfirmOpen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val job = scope.launch {
            while (isActive) {
                AppGraph.repo?.getDailyStat(todayLocalDate())?.let {
                    todayAppSeconds = it.appSeconds
                    todayFeedSeconds = it.feedSeconds
                }
                val usage = UsageTracker.getLiveUsage()
                live = LiveSample(usage.appMs / 1000, usage.feedMs / 1000)
                delay(1000)
            }
        }
        onDispose { job.cancel() }
    }

    // Flushed seconds from the database plus unflushed in-memory time.
    val appTotal = todayAppSeconds + live.appSeconds
    val feedTotal = todayFeedSeconds + live.feedSeconds

    if (soundHintOpen) {
        AlertDialog(
            onDismissRequest = { soundHintOpen = false },
            title = { Text("Sound") },
            text = {
                Text(
                    "Pronunciation stays silent when the ring/silent switch is on. " +
                        "Turn it off to hear the words.",
                )
            },
            confirmButton = {
                TextButton(onClick = { soundHintOpen = false }) { Text("OK") }
            },
        )
    }

    if (clearConfirmOpen) {
        AlertDialog(
            onDismissRequest = { clearConfirmOpen = false },
            title = { Text("Clear all data?") },
            text = {
                Text(
                    "Progress, flags, stats and settings are erased. " +
                        "Word buckets are kept and re-seeded.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clearConfirmOpen = false
                    scope.launch { AppGraph.resetData() }
                }) {
                    Text("Clear", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }

    Screen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (settings.todayReadout) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.m))
                        .background(colors.surface)
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                ) {
                    Text(
                        "Today · App ${formatClock(appTotal)} · Feed ${formatClock(feedTotal)}",
                        color = colors.textSecondary,
                        fontSize = FontSize.caption,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }

            Group("Feed") {
                SwitchRow("Auto pronunciation", settings.autoPronounce) { v ->
                    settingsStore.update(settings.copy(autoPronounce = v))
                }
                SelectRow("Speech rate", RATE_OPTIONS, settings.speechRate) { v ->
                    settingsStore.update(settings.copy(speechRate = v))
                }
                SelectRow("Meaning display", MEANING_OPTIONS, settings.meaningMode) { v ->
                    settingsStore.update(settings.copy(meaningMode = v))
                }
                SwitchRow("Progress bar in feed", settings.progressBar) { v ->
                    settingsStore.update(settings.copy(progressBar = v))
                }
                SwitchRow("Progress bar dragging", settings.progressBarDrag) { v ->
                    settingsStore.update(settings.copy(progressBarDrag = v))
                }
                SwitchRow("Progress bar in word page", settings.wordProgressBar) { v ->
                    settingsStore.update(settings.copy(wordProgressBar = v))
                }
                SwitchRow("Search in feed", settings.feedSearch) { v ->
                    settingsStore.update(settings.copy(feedSearch = v))
                }
            }

            Group("Appearance") {
                OptionRow("Theme", THEME_OPTIONS, settings.theme) { v ->
                    settingsStore.update(settings.copy(theme = v))
                }
            }

            Group("General") {
                SwitchRow("Today time readout", settings.todayReadout) { v ->
                    settingsStore.update(settings.copy(todayReadout = v))
                }
            }

            Group("About") {
                ValueRow("Version", APP_VERSION)
                SettingRow(label = "Sound hint", onClick = {
                    settingsStore.update(settings.copy(silentHintShown = true))
                    soundHintOpen = true
                }) {
                    Text("Show again", color = colors.textTertiary, fontSize = FontSize.body)
                }
                SettingRow(label = "Clear all data", onClick = { clearConfirmOpen = true }) {
                    Text("Erase", color = colors.textTertiary, fontSize = FontSize.body)
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val colors = LocalFeedahColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            title.uppercase(),
            color = colors.textTertiary,
            fontSize = FontSize.caption,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.m))
                .background(colors.surface),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = LocalFeedahColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = Spacing.m, vertical = 12.dp)
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.text, fontSize = FontSize.body)
        trailing()
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    val colors = LocalFeedahColors.current
    SettingRow(label = label) {
        Switch(
            checked = value,
            onCheckedChange = onValueChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    val colors = LocalFeedahColors.current
    SettingRow(label = label) {
        Text(value, color = colors.textTertiary, fontSize = FontSize.body)
    }
}

@Composable
private fun OptionRow(
    label: String,
    options: List<Pair<ThemeMode, String>>,
    value: ThemeMode,
    onChange: (ThemeMode) -> Unit,
) {
    val colors = LocalFeedahColors.current
    SettingRow(label = label) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for ((optionValue, optionLabel) in options) {
                val active = optionValue == value
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Radius.l))
                        .background(if (active) colors.accent else colors.background)
                        .clickable { onChange(optionValue) }
                        .padding(horizontal = Spacing.m, vertical = 6.dp),
                ) {
                    Text(
                        optionLabel,
                        color = if (active) Color.White else colors.textSecondary,
                        fontSize = FontSize.caption,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SelectRow(
    label: String,
    options: List<Pair<T, String>>,
    value: T,
    onChange: (T) -> Unit,
) {
    val colors = LocalFeedahColors.current
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == value }
    Box {
        SettingRow(label = label, onClick = { open = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current?.second ?: "", color = colors.textSecondary, fontSize = FontSize.body)
                Icon(
                    ChevronDownIcon,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((optionValue, optionLabel) in options) {
                val active = optionValue == value
                DropdownMenuItem(
                    text = {
                        Text(
                            optionLabel,
                            color = if (active) colors.accent else colors.text,
                            fontSize = FontSize.body,
                        )
                    },
                    onClick = {
                        onChange(optionValue)
                        open = false
                    },
                )
            }
        }
    }
}
