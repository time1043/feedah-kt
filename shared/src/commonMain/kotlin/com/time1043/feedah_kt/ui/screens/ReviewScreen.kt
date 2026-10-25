package com.time1043.feedah_kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.time1043.feedah_kt.AppGraph
import com.time1043.feedah_kt.data.db.UsageTracker
import com.time1043.feedah_kt.data.db.WordRow
import com.time1043.feedah_kt.logic.formatDayLabel
import com.time1043.feedah_kt.platform.speakWord
import com.time1043.feedah_kt.ui.components.ChevronDownIcon
import com.time1043.feedah_kt.ui.components.ProgressBar
import com.time1043.feedah_kt.ui.components.SearchIcon
import com.time1043.feedah_kt.ui.components.WordCard
import com.time1043.feedah_kt.ui.navigation.Review
import com.time1043.feedah_kt.ui.navigation.Search
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DAY_SHAPE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/**
 * Review pass over one of three flavors: a round's historical flag snapshot,
 * a day's completed words across buckets, or the bucket's current flag set.
 * The queue is snapshotted on entry; unflagging during the session updates
 * the state but the word stays until the next pass. Nothing is recorded — no
 * pointer, no rounds, no word counts — though the time spent counts as
 * studying. The one durable trace is the local resume cursor (never synced).
 */
@Composable
fun ReviewScreen(nav: NavHostController, route: Review) {
    val colors = LocalFeedahColors.current
    val settings by AppGraph.settings!!.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val bucketId = route.bucket?.takeIf { it.isNotEmpty() } ?: settings.activeBucketId
    val round = route.round ?: 0
    val hasRound = round > 0
    val day = route.day?.takeIf { DAY_SHAPE.matches(it) } ?: ""
    // Resume-cursor identity: one key per queue, so every flavor remembers its
    // own position (day queues span buckets, hence no bucket in that key).
    val sessionKey = when {
        hasRound -> "round:$bucketId:$round"
        day.isNotEmpty() -> "day:$day"
        else -> "flagged:$bucketId"
    }

    var ready by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(emptyList<WordRow>()) }
    var startIndex by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf(0) }
    var suppressSettle by remember { mutableStateOf(false) }
    var endLinger by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { queue.size + 1 })
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(sessionKey) {
        val repo = AppGraph.repo ?: return@LaunchedEffect
        val list = when {
            hasRound -> repo.getRoundFlaggedWords(bucketId, round)
            day.isNotEmpty() -> repo.getWordsCompletedOn(day)
            else -> repo.getFlaggedWords(bucketId)
        }
        // Resume at the furthest card settled last time; a finished or stale
        // cursor (past the queue's end) restarts from the top.
        val resume = repo.getReviewCursor(sessionKey)
        val start = if (resume > 0 && resume < list.size) resume else 0
        queue = list
        startIndex = start
        current = start
        ready = true
        if (settings.autoPronounce) {
            AppGraph.tts?.let { tts ->
                list.getOrNull(start)?.let { speakWord(tts, it.text, settings.speechRate) }
            }
        }
    }

    // Review time counts as studying: same tracking as the feed screen.
    LifecycleResumeEffect(Unit) {
        UsageTracker.startFeedUsage()
        onPauseOrDispose {
            UsageTracker.pauseFeedUsage()
            UsageTracker.flushUsage()
        }
    }

    fun settle(index: Int) {
        val repo = AppGraph.repo ?: return
        if (!endLinger) {
            if (index >= queue.size) {
                current = index
                // The queue was walked through: the next entry starts fresh.
                repo.clearReviewCursor(sessionKey)
                endLinger = true
                scope.launch {
                    delay(2000)
                    endLinger = false
                    nav.popBackStack()
                }
                return
            }
            current = index
            repo.saveReviewCursor(sessionKey, index)
            if (settings.autoPronounce) {
                AppGraph.tts?.let { tts ->
                    queue.getOrNull(index)?.let { speakWord(tts, it.text, settings.speechRate) }
                }
            }
            UsageTracker.flushUsage()
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (suppressSettle) {
                suppressSettle = false
            } else {
                settle(page)
            }
        }
    }
    LaunchedEffect(dragged) {
        if (dragged) suppressSettle = false
    }

    fun toggleFlagged(index: Int) {
        val repo = AppGraph.repo ?: return
        val word = queue.getOrNull(index) ?: return
        val next = !word.flagged
        // A day review may span several buckets — flag against the word's own.
        repo.setFlag(word.bucketId, word.position, next)
        queue = queue.mapIndexed { i, w -> if (i == index) w.copy(flagged = next) else w }
    }

    fun jumpTo(index: Int) {
        suppressSettle = true
        scope.launch { pagerState.scrollToPage(index) }
        current = index
    }

    // Re-snap to the card the user is on whenever composition settles (the
    // pager lands pages at full height by construction, so the FlatList
    // viewport-drift class of bugs does not apply; the initial jump below
    // still mirrors the original).
    LaunchedEffect(ready) {
        if (ready && queue.isNotEmpty()) {
            suppressSettle = true
            pagerState.scrollToPage(current.coerceIn(0, queue.size))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)),
    ) {
        if (ready && queue.isEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                Icon(
                    ChevronDownIcon,
                    contentDescription = "Back",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable { nav.popBackStack() },
                )
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (day.isNotEmpty()) "Nothing completed that day" else "Nothing flagged yet",
                    color = colors.textTertiary,
                    fontSize = FontSize.body,
                )
            }
            return@Column
        }

        Column(Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s)) {
            Box(Modifier.padding(top = Spacing.xs)) {
                ProgressBar(
                    value = current,
                    max = queue.size + 1,
                    interactive = true,
                    onScrub = ::jumpTo,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s, vertical = Spacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ChevronDownIcon,
                    contentDescription = "Close review",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable { nav.popBackStack() },
                )
                // A day review may span several buckets; the title follows the
                // bucket of the word currently on screen (the last one on the
                // end card).
                val currentWord = queue.getOrNull(minOf(current, queue.size - 1))
                val flavor = when {
                    hasRound -> "Review · Round $round"
                    day.isNotEmpty() -> "Review · ${formatDayLabel(day)}"
                    else -> "Review"
                }
                Text(
                    flavor + (currentWord?.let { " · ${it.bucketId}" } ?: ""),
                    color = colors.textSecondary,
                    fontSize = FontSize.caption,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Icon(
                    SearchIcon,
                    contentDescription = "Search words",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .padding(11.dp)
                        .clickable { nav.navigate(Search()) },
                )
            }
        }

        VerticalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            if (page < queue.size) {
                val word = queue[page]
                WordCard(
                    position = word.position,
                    text = word.text,
                    meaning = word.meaning,
                    forms = word.forms,
                    ipa = word.ipa,
                    flagged = word.flagged,
                    meaningMode = settings.meaningMode,
                    onReplay = {
                        AppGraph.tts?.let { tts -> speakWord(tts, word.text, settings.speechRate) }
                    },
                    onToggleFlagged = { toggleFlagged(page) },
                )
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
                ) {
                    Text(
                        "Review complete",
                        color = colors.text,
                        fontSize = FontSize.title,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Swipe up to finish",
                        color = colors.textTertiary,
                        fontSize = FontSize.body,
                    )
                }
            }
        }
    }
}
