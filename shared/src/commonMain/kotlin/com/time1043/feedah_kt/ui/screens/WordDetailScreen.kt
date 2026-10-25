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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.time1043.feedah_kt.AppGraph
import com.time1043.feedah_kt.data.db.WordRow
import com.time1043.feedah_kt.logic.SearchReset
import com.time1043.feedah_kt.platform.speakWord
import com.time1043.feedah_kt.ui.components.ChevronDownIcon
import com.time1043.feedah_kt.ui.components.ProgressBar
import com.time1043.feedah_kt.ui.components.SearchIcon
import com.time1043.feedah_kt.ui.components.WordCard
import com.time1043.feedah_kt.ui.navigation.Search
import com.time1043.feedah_kt.ui.navigation.WordDetail
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Word browser opened from search or the words list: swipe up/down through
 * the whole bucket, scrub with the progress bar, flag words. Purely a lookup
 * — none of it counts as studying, moves the pointer, or records time.
 */
@Composable
fun WordDetailScreen(nav: NavHostController, route: WordDetail) {
    val colors = LocalFeedahColors.current
    val settings by AppGraph.settings!!.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // The bucket is pinned by the opener (word list passes its tab); search
    // omits it and the active bucket is used.
    val bucketId = route.bucket.ifEmpty { settings.activeBucketId }
    // Search results mark this page fromSearch; the search icon then returns
    // to that search instead of opening another one.
    val fromSearch = route.fromSearch

    var ready by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf(emptyList<WordRow>()) }
    var current by remember { mutableStateOf(0) }
    var suppressSettle by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { words.size })
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(bucketId, route.position) {
        val repo = AppGraph.repo ?: return@LaunchedEffect
        val list = repo.getWords(bucketId)
        val requested = route.position
        val initial = if (requested in 1..list.size) requested - 1 else 0
        words = list
        current = initial
        ready = true
        if (settings.autoPronounce) {
            AppGraph.tts?.let { tts ->
                list.getOrNull(initial)?.let { speakWord(tts, it.text, settings.speechRate) }
            }
        }
    }

    // Alignment insurance: land exactly on a card once composition settles.
    LaunchedEffect(ready) {
        if (ready && words.isNotEmpty()) {
            suppressSettle = true
            pagerState.scrollToPage(current.coerceIn(0, words.size - 1))
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (suppressSettle) {
                suppressSettle = false
            } else {
                current = page
                if (settings.autoPronounce) {
                    AppGraph.tts?.let { tts ->
                        words.getOrNull(page)?.let { speakWord(tts, it.text, settings.speechRate) }
                    }
                }
            }
        }
    }
    LaunchedEffect(dragged) {
        if (dragged) suppressSettle = false
    }

    fun jumpTo(index: Int) {
        suppressSettle = true
        scope.launch { pagerState.scrollToPage(index) }
        current = index
    }

    fun toggleFlagged(index: Int) {
        val repo = AppGraph.repo ?: return
        val word = words.getOrNull(index) ?: return
        val next = !word.flagged
        repo.setFlag(bucketId, word.position, next)
        words = words.mapIndexed { i, w -> if (i == index) w.copy(flagged = next) else w }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)),
    ) {
        Column(Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s)) {
            if (settings.wordProgressBar) {
                Box(Modifier.padding(top = Spacing.xs)) {
                    ProgressBar(
                        value = current,
                        max = maxOf(words.size, 1),
                        interactive = settings.progressBarDrag,
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
                    contentDescription = "Back",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable { nav.popBackStack() },
                )
                // Results open this page marked fromSearch, so the icon goes
                // back to that search for a fresh lookup — bar cleared, keyboard
                // up; the back gesture keeps the query. Other origins navigate
                // into a fresh search, so a word card never lingers beneath it.
                Icon(
                    SearchIcon,
                    contentDescription = "Search words",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .padding(11.dp)
                        .clickable {
                            if (fromSearch) {
                                SearchReset.requestSearchReset()
                                nav.popBackStack()
                            } else {
                                val entry = nav.currentBackStackEntry
                                nav.navigate(Search()) {
                                    if (entry != null) {
                                        popUpTo(entry.id) { inclusive = true }
                                    }
                                }
                            }
                        },
                )
            }
        }

        if (!ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // The whole bucket loads before the first card can show — keep
                // the wait visible instead of a blank page.
                CircularProgressIndicator(color = colors.textTertiary)
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
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
            }
        }
    }
}
