package com.time1043.feedah_kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import com.time1043.feedah_kt.AppGraph
import com.time1043.feedah_kt.data.db.FeedahRepo
import com.time1043.feedah_kt.data.db.RoundWordRow
import com.time1043.feedah_kt.logic.DailyPointerRow
import com.time1043.feedah_kt.logic.DailyUsage
import com.time1043.feedah_kt.logic.calendarDaysBetween
import com.time1043.feedah_kt.logic.computeDailyUsage
import com.time1043.feedah_kt.logic.dayBounds
import com.time1043.feedah_kt.logic.formatDayLabel
import com.time1043.feedah_kt.logic.formatMinutes
import com.time1043.feedah_kt.logic.formatYearMonthDay
import com.time1043.feedah_kt.logic.nowMs
import com.time1043.feedah_kt.logic.todayLocalDate
import com.time1043.feedah_kt.logic.DayJump
import com.time1043.feedah_kt.ui.components.ChevronLeftIcon
import com.time1043.feedah_kt.ui.components.ChevronRightIcon
import com.time1043.feedah_kt.ui.components.Heatmap
import com.time1043.feedah_kt.ui.components.RoundBar
import com.time1043.feedah_kt.ui.components.RoundWordStatus
import com.time1043.feedah_kt.ui.components.Screen
import com.time1043.feedah_kt.ui.navigation.Review
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Radius
import com.time1043.feedah_kt.ui.theme.Spacing
import kotlin.math.floor
import kotlinx.coroutines.launch

private enum class Metric { words, minutes }

private val WORD_THRESHOLDS = listOf(10L, 30L, 100L)
private val MINUTE_THRESHOLDS = listOf(10L, 30L, 60L)

internal data class RoundDisplay(
    val round: Int,
    val statuses: List<RoundWordStatus>,
    val days: Int,
    val done: Boolean,
    val pointer: Int,
    val wordCount: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val green: Int,
    val red: Int,
)

private fun countOf(statuses: List<RoundWordStatus>, status: RoundWordStatus): Int =
    statuses.count { it == status }

private fun findRoundStartedAt(
    pointers: List<DailyPointerRow>,
    round: Int,
    wordCount: Int,
): Long {
    if (pointers.isEmpty() || wordCount <= 0) return 0
    val startGlobalPos = (round - 1L) * wordCount + 1
    for (p in pointers) {
        if (p.globalPosition >= startGlobalPos) {
            return dayBounds(p.day).start
        }
    }
    return 0
}

private fun toStatuses(words: List<RoundWordRow>, wordCount: Int): List<RoundWordStatus> {
    val statuses = MutableList(wordCount) { RoundWordStatus.gray }
    for (word in words) {
        if (word.position < 1 || word.position > wordCount) continue
        statuses[word.position - 1] = if (word.flagged) RoundWordStatus.red else RoundWordStatus.green
    }
    return statuses
}

private fun formatRoundLabel(round: RoundDisplay): String {
    val startDate = when {
        round.startedAt > 0 -> formatYearMonthDay(round.startedAt)
        round.finishedAt > 0 -> formatYearMonthDay(round.finishedAt)
        else -> formatYearMonthDay(todayLocalDate())
    }
    return "Round ${round.round} · $startDate · ${round.days}d · ${round.pointer}"
}

internal suspend fun loadRounds(repo: FeedahRepo, bucketId: String): List<RoundDisplay> {
    val wordCount = repo.getWordCount(bucketId)
    val history = repo.getRoundHistory(bucketId)
    val progress = repo.getProgress(bucketId)
    val pointers = repo.getDailyPointersForBucket(bucketId)
    val now = nowMs()
    val displays = ArrayList<RoundDisplay>()
    val knownRounds = history.map { it.round }.toHashSet()

    for (row in history) {
        var startedAt = row.startedAt
        if (startedAt == 0L || (row.finishedAt > 0 && startedAt >= row.finishedAt)) {
            val recovered = findRoundStartedAt(pointers, row.round, wordCount)
            if (recovered > 0 && (row.finishedAt == 0L || recovered < row.finishedAt)) {
                startedAt = recovered
            }
        }
        val days = calendarDaysBetween(startedAt, if (row.finishedAt > 0) row.finishedAt else now)
        val statuses = toStatuses(repo.getRoundWords(bucketId, row.round), wordCount)
        displays.add(
            RoundDisplay(
                round = row.round,
                statuses = statuses,
                days = days,
                done = true,
                pointer = wordCount,
                wordCount = wordCount,
                startedAt = startedAt,
                finishedAt = row.finishedAt,
                green = countOf(statuses, RoundWordStatus.green),
                red = countOf(statuses, RoundWordStatus.red),
            ),
        )
    }

    // Fallback: If earlier completed rounds (r < progress.round) are missing from
    // history, but we have round words for them, still display them so the user
    // never loses their historical round bars.
    for (r in 1 until progress.round) {
        if (r !in knownRounds) {
            val words = repo.getRoundWords(bucketId, r)
            if (words.isNotEmpty()) {
                val statuses = toStatuses(words, wordCount)
                val startedAt = findRoundStartedAt(pointers, r, wordCount)
                val nextRoundStartedAt = if (r + 1 == progress.round) {
                    progress.startedAt
                } else {
                    findRoundStartedAt(pointers, r + 1, wordCount)
                }
                val finishedAt = if (nextRoundStartedAt > 0) nextRoundStartedAt else now
                val days = if (startedAt > 0) calendarDaysBetween(startedAt, finishedAt) else 1
                displays.add(
                    RoundDisplay(
                        round = r,
                        statuses = statuses,
                        days = days,
                        done = true,
                        pointer = wordCount,
                        wordCount = wordCount,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        green = countOf(statuses, RoundWordStatus.green),
                        red = countOf(statuses, RoundWordStatus.red),
                    ),
                )
            }
        }
    }

    displays.sortBy { it.round }

    var currentStartedAt = progress.startedAt
    if (currentStartedAt == 0L) {
        val recovered = findRoundStartedAt(pointers, progress.round, wordCount)
        if (recovered > 0) currentStartedAt = recovered
    }
    val currentDays = if (currentStartedAt > 0) calendarDaysBetween(currentStartedAt, now) else 1
    val currentStatuses = toStatuses(repo.getRoundWords(bucketId, progress.round), wordCount)
    displays.add(
        RoundDisplay(
            round = progress.round,
            statuses = currentStatuses,
            days = currentDays,
            done = progress.pointer >= wordCount,
            pointer = progress.pointer,
            wordCount = wordCount,
            startedAt = currentStartedAt,
            finishedAt = 0,
            green = countOf(currentStatuses, RoundWordStatus.green),
            red = countOf(currentStatuses, RoundWordStatus.red),
        ),
    )
    return displays
}

private fun currentYear(): Int =
    com.time1043.feedah_kt.logic.todayLocalDate().substring(0, 4).toInt()

@Composable
fun StatsScreen(nav: NavHostController) {
    val colors = LocalFeedahColors.current
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf(emptyMap<String, DailyUsage>()) }
    var metric by remember { mutableStateOf(Metric.words) }
    var year by remember { mutableStateOf(currentYear()) }
    var selectedDay by remember { mutableStateOf(todayLocalDate()) }
    // Round tabs: only buckets that have been started appear here.
    var roundTabs by remember { mutableStateOf(emptyList<String>()) }
    var roundTab by remember { mutableStateOf("") }
    var rounds by remember { mutableStateOf(emptyList<RoundDisplay>()) }

    LifecycleResumeEffect(Unit) {
        scope.launch {
            // Returning to the tab always re-anchors to today: a browsed day is
            // a momentary readout, and this fires when focus returns from a
            // pushed review too. The reset is published, so the words list
            // mirrors it on its next focus.
            val day = todayLocalDate()
            selectedDay = day
            year = currentYear()
            DayJump.publishSelectedDay(day)

            val repo = AppGraph.repo ?: return@launch
            val stats = repo.listDailyStats()
            val pointers = repo.listDailyPointers()
            val buckets = repo.listBuckets()
            usage = computeDailyUsage(stats, pointers)

            val active = AppGraph.settings!!.settings.value.activeBucketId
            val tabs = ArrayList<String>()
            for (bucket in buckets) {
                val progress = repo.getProgress(bucket.id)
                val history = repo.getRoundHistory(bucket.id)
                if (progress.pointer > 0 || progress.round > 1 || history.isNotEmpty()) {
                    tabs.add(bucket.id)
                }
            }
            roundTabs = tabs
            val tab = if (active in tabs) active else (tabs.firstOrNull() ?: "")
            roundTab = tab
            rounds = if (tab.isEmpty()) emptyList() else loadRounds(repo, tab)
        }
        onPauseOrDispose { }
    }

    val dayUsage = usage[selectedDay]
    val isToday = selectedDay == todayLocalDate()
    val heatValues: Map<String, Long> = usage.mapValues { (_, value) ->
        if (metric == Metric.words) value.words else floor(value.feedSeconds / 60.0 + 0.5).toLong()
    }

    // Year totals for the year the heatmap is showing, subordinate to the
    // selected day readout above.
    val yearPrefix = "$year-"
    var yearWords = 0L
    var yearFeedSeconds = 0L
    var yearAppSeconds = 0L
    for ((day, value) in usage) {
        if (day.startsWith(yearPrefix)) {
            yearWords += value.words
            yearFeedSeconds += value.feedSeconds
            yearAppSeconds += value.appSeconds
        }
    }

    Screen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            // ===== today card =====
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    formatDayLabel(selectedDay) + if (isToday) " · today" else "",
                    color = colors.textTertiary,
                    fontSize = FontSize.caption,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBlock(
                        modifier = Modifier.weight(1f),
                        label = "Words",
                        value = "${dayUsage?.words ?: 0}",
                        onPress = if (dayUsage != null && dayUsage.words > 0) {
                            { nav.navigate(Review(day = selectedDay)) }
                        } else {
                            null
                        },
                    )
                    StatBlock(Modifier.weight(1f), label = "Studying", value = formatMinutes(dayUsage?.feedSeconds ?: 0))
                    StatBlock(Modifier.weight(1f), label = "In app", value = formatMinutes(dayUsage?.appSeconds ?: 0))
                }
            }

            // ===== heatmap card =====
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.m))
                    .background(colors.surface)
                    .padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        MetricPill("Words", metric == Metric.words) { metric = Metric.words }
                        MetricPill("Minutes", metric == Metric.minutes) { metric = Metric.minutes }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            ChevronLeftIcon,
                            contentDescription = "Previous year",
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { year -= 1 },
                        )
                        Text(
                            "$year",
                            color = colors.text,
                            fontSize = FontSize.body,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(48.dp),
                        )
                        Icon(
                            ChevronRightIcon,
                            contentDescription = "Next year",
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { year += 1 },
                        )
                    }
                }
                Heatmap(
                    year = year,
                    values = heatValues,
                    thresholds = if (metric == Metric.words) WORD_THRESHOLDS else MINUTE_THRESHOLDS,
                    selectedDay = selectedDay,
                    onSelectDay = { day ->
                        selectedDay = day
                        // Publish every selection change — the words list mirrors
                        // the heatmap day on its next focus, tap or reset alike.
                        DayJump.publishSelectedDay(day)
                    },
                )
                Text(
                    "$yearWords words · ${formatMinutes(yearFeedSeconds)} studying · ${formatMinutes(yearAppSeconds)} in app",
                    color = colors.textTertiary,
                    fontSize = FontSize.caption,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ===== rounds =====
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(
                    "ROUNDS",
                    color = colors.textSecondary,
                    fontSize = FontSize.caption,
                    fontWeight = FontWeight.SemiBold,
                )
                if (roundTabs.isEmpty()) {
                    Text(
                        "No rounds yet — start studying first.",
                        color = colors.textTertiary,
                        fontSize = FontSize.body,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        for (id in roundTabs) {
                            MetricPill(id, id == roundTab) {
                                if (id != roundTab) {
                                    roundTab = id
                                    scope.launch {
                                        rounds = AppGraph.repo?.let { loadRounds(it, id) } ?: emptyList()
                                    }
                                }
                            }
                        }
                    }
                    for (round in rounds) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    formatRoundLabel(round),
                                    color = colors.textTertiary,
                                    fontSize = FontSize.caption,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CountGroup(round.green, colors.success, colors.textSecondary, onClick = null)
                                    CountGroup(
                                        round.red,
                                        colors.danger,
                                        colors.textSecondary,
                                        onClick = if (round.red > 0) {
                                            { nav.navigate(Review(bucket = roundTab, round = round.round)) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                            RoundBar(round.statuses)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(modifier: Modifier = Modifier, label: String, value: String, onPress: (() -> Unit)? = null) {
    val colors = LocalFeedahColors.current
    Column(
        modifier
            .clickable(enabled = onPress != null) { onPress?.invoke() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                color = colors.text,
                fontSize = FontSize.title,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            if (onPress != null) {
                Icon(
                    ChevronRightIcon,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(label, color = colors.textTertiary, fontSize = FontSize.caption, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MetricPill(label: String, active: Boolean, onPress: () -> Unit) {
    val colors = LocalFeedahColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.l))
            .background(if (active) colors.accent else colors.background)
            .clickable(onClick = onPress)
            .padding(horizontal = Spacing.m, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (active) Color.White else colors.textSecondary,
            fontSize = FontSize.caption,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CountGroup(count: Int, dotColor: Color, countColor: Color, onClick: (() -> Unit)?) {
    Row(
        Modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            "$count",
            color = countColor,
            fontSize = FontSize.caption,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(end = Spacing.xs),
        )
    }
}

