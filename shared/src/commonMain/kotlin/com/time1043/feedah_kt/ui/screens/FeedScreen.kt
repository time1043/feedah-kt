package com.time1043.feedah_kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.time1043.feedah_kt.AppGraph
import com.time1043.feedah_kt.data.db.Settings
import com.time1043.feedah_kt.data.db.UsageTracker
import com.time1043.feedah_kt.data.db.WordRow
import com.time1043.feedah_kt.platform.speakWord
import com.time1043.feedah_kt.ui.components.ChevronDownIcon
import com.time1043.feedah_kt.ui.components.ProgressBar
import com.time1043.feedah_kt.ui.components.SearchIcon
import com.time1043.feedah_kt.ui.components.WordCard
import com.time1043.feedah_kt.ui.navigation.Feed
import com.time1043.feedah_kt.ui.navigation.Search
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Radius
import com.time1043.feedah_kt.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FeedMode { study, browse }

/** Fullscreen swipe feed: one word card per page, immersive, no tab bar. */
@Composable
fun FeedScreen(nav: NavHostController, route: Feed) {
    val colors = LocalFeedahColors.current
    val settingsStore = AppGraph.settings!!
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Home pins the bucket on navigation; without the param the active bucket
    // from settings is used.
    val bucketId = route.bucket.ifEmpty { settings.activeBucketId }

    var ready by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf(emptyList<WordRow>()) }
    var round by remember { mutableStateOf(1) }
    var pointer by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf(0) }
    // Studying records; browsing (after a progress bar drag) records nothing.
    var mode by remember { mutableStateOf(FeedMode.study) }
    var suppressSettle by remember { mutableStateOf(false) }
    // While the round-complete page lingers, gestures record nothing.
    var roundAdvanceLinger by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { words.size + 1 })
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(bucketId) {
        val repo = AppGraph.repo ?: return@LaunchedEffect
        // Settings are loaded before any screen renders (AppGraph.init), so the
        // bucket id is already the persisted one here.
        val progress = repo.getProgress(bucketId)
        val list = repo.getWords(bucketId)
        words = list
        round = progress.round
        pointer = progress.pointer
        current = minOf(progress.pointer, list.size)
        mode = FeedMode.study
        ready = true
        // Speak the card the feed opens on; other cards speak on settle.
        if (settings.autoPronounce) {
            AppGraph.tts?.let { tts ->
                list.getOrNull(current)?.let { speakWord(tts, it.text, settings.speechRate) }
            }
        }
    }

    // Feed time accrues only while this screen is on top; the app-level
    // backgrounding pause lives in the platform shell (MainActivity).
    LifecycleResumeEffect(Unit) {
        UsageTracker.startFeedUsage()
        onPauseOrDispose {
            UsageTracker.pauseFeedUsage()
            UsageTracker.flushUsage()
        }
    }

    // Feed opens exactly on a card boundary.
    LaunchedEffect(ready) {
        if (ready) {
            suppressSettle = true
            pagerState.scrollToPage(minOf(current, words.size))
        }
    }

    // Studying counts the card you just LEFT BEHIND: landing on a card
    // completes the previous one, so the card on screen is still in progress
    // and exiting there loses nothing. All repo calls are synchronous, so the
    // closure reads/writes the state delegates directly.
    val settle: (Int) -> Unit = { page ->
        val repo = AppGraph.repo
        if (repo != null && !roundAdvanceLinger) {
            if (page >= words.size) {
                // The footer is only reachable by swiping past the last card.
                if (mode == FeedMode.study && pointer >= words.size - 1 && page > pointer) {
                    current = page
                    val next = repo.advancePointer(bucketId, page)
                    pointer = next.pointer
                    if (next.pointer >= words.size) {
                        val progress = repo.startNextRound(bucketId)
                        round = progress.round
                        pointer = progress.pointer
                        // Let the completion page linger, then open the new round.
                        roundAdvanceLinger = true
                        scope.launch {
                            delay(2000)
                            roundAdvanceLinger = false
                            suppressSettle = true
                            pagerState.scrollToPage(0)
                            current = 0
                        }
                    }
                }
            } else {
                // Only studying mode records; browsing is free navigation.
                if (mode == FeedMode.study && page > pointer) {
                    pointer = repo.advancePointer(bucketId, page).pointer
                }
                current = page
                if (settings.autoPronounce) {
                    AppGraph.tts?.let { tts ->
                        words.getOrNull(page)?.let { speakWord(tts, it.text, settings.speechRate) }
                    }
                }
                UsageTracker.flushUsage()
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (suppressSettle) {
                // A programmatic scroll landed; drop the suppression now.
                suppressSettle = false
            } else {
                settle(page)
            }
        }
    }
    // A real gesture is starting: drop any stale jump suppression left behind
    // by an instant scrollToPage.
    LaunchedEffect(dragged) {
        if (dragged) suppressSettle = false
    }

    fun toggleFlagged(index: Int) {
        val repo = AppGraph.repo ?: return
        val word = words[index]
        val next = !word.flagged
        repo.setFlag(bucketId, word.position, next)
        words = words.mapIndexed { i, w -> if (i == index) w.copy(flagged = next) else w }
    }

    fun jumpTo(index: Int) {
        // Dragging the bar switches to browse mode: free navigation, zero
        // recording. Studying resumes from the frontier via the header control.
        suppressSettle = true
        scope.launch { pagerState.scrollToPage(index) }
        current = index
        mode = FeedMode.browse
    }

    fun resumeStudy() {
        // Return to the first unlearned card; instant scroll so no settle fires.
        val target = minOf(pointer, words.size)
        mode = FeedMode.study
        suppressSettle = true
        scope.launch { pagerState.scrollToPage(target) }
        current = target
    }

    if (!ready) {
        Box(Modifier.fillMaxSize().background(colors.background))
        return
    }

    val pageCount = words.size + 1

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)),
    ) {
        // Fixed header: progress bar row, then controls row; the card area
        // always starts below it, so spacing stays constant.
        Column(Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s)) {
            if (settings.progressBar) {
                Box(Modifier.padding(top = Spacing.xs)) {
                    ProgressBar(
                        value = current,
                        max = pageCount,
                        interactive = settings.progressBarDrag && pointer > 0,
                        onScrub = ::jumpTo,
                    )
                }
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
                    contentDescription = "Close feed",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable { nav.popBackStack() },
                )
                if (mode == FeedMode.browse) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radius.l))
                            .background(colors.accent)
                            .clickable { resumeStudy() }
                            .padding(horizontal = Spacing.m, vertical = 6.dp),
                    ) {
                        Text(
                            "Resume studying",
                            color = Color.White,
                            fontSize = FontSize.caption,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Spacer(Modifier)
                }
                if (settings.feedSearch) {
                    Icon(
                        SearchIcon,
                        contentDescription = "Search words",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .padding(11.dp)
                            .clickable { nav.navigate(Search(pinnedBucket = bucketId)) },
                    )
                } else {
                    Spacer(Modifier)
                }
            }
        }

        VerticalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            if (page < words.size) {
                val word = words[page]
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
                        "Round $round complete",
                        color = colors.text,
                        fontSize = FontSize.title,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Round ${round + 1} starts in a moment",
                        color = colors.textTertiary,
                        fontSize = FontSize.body,
                    )
                }
            }
        }
    }
}
