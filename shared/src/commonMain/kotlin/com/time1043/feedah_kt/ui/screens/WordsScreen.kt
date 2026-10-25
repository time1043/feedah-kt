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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.time1043.feedah_kt.AppGraph
import com.time1043.feedah_kt.data.db.BucketInfo
import com.time1043.feedah_kt.data.db.WordRow
import com.time1043.feedah_kt.logic.DayJump
import com.time1043.feedah_kt.logic.pushOnce
import com.time1043.feedah_kt.ui.components.BucketTabs
import com.time1043.feedah_kt.ui.components.BookmarkFilledIcon
import com.time1043.feedah_kt.ui.components.CloseIcon
import com.time1043.feedah_kt.ui.components.EyeIcon
import com.time1043.feedah_kt.ui.components.EyeOffIcon
import com.time1043.feedah_kt.ui.components.HomeIcon
import com.time1043.feedah_kt.ui.components.ProgressBar
import com.time1043.feedah_kt.ui.components.SearchIcon
import com.time1043.feedah_kt.ui.components.Screen
import com.time1043.feedah_kt.ui.navigation.WordDetail
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val ROW_HEIGHT = 52

@Composable
fun WordsScreen(nav: NavHostController) {
    val colors = LocalFeedahColors.current
    val settingsStore = AppGraph.settings!!
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var buckets by remember { mutableStateOf(emptyList<BucketInfo>()) }
    var tab by remember { mutableStateOf("") }
    var words by remember { mutableStateOf(emptyList<WordRow>()) }
    var pendingJump by remember { mutableStateOf(-1) }
    val listState = rememberLazyListState()

    LifecycleResumeEffect(Unit) {
        scope.launch {
            val repo = AppGraph.repo ?: return@launch
            val list = repo.listBuckets()
            buckets = list
            // The list mirrors the stats heatmap's selected day: it opens at that
            // day's first completed word, and the day-review's canonical bucket
            // order decides which tab shows it. A day without records anchors back
            // at the very first word. No change since the last positioning —
            // nothing moves.
            val day = DayJump.consumeSelectedDayChange()
            val first = day?.let { repo.getWordsCompletedOn(it).firstOrNull() }
            val target = first?.bucketId
                ?: (if (tab.isNotEmpty() && list.any { it.id == tab }) {
                    tab
                } else {
                    list.firstOrNull { it.id == settings.activeBucketId }?.id
                        ?: list.firstOrNull()?.id
                        ?: ""
                })
            tab = target
            if (target.isNotEmpty()) words = repo.getWords(target)
            pendingJump = first?.position?.minus(1) ?: if (day != null) 0 else pendingJump
        }
        onPauseOrDispose { }
    }

    // Applies a day change once the day's words are committed: scrolls the
    // list to its anchor (the day's first word, or the very first word for an
    // empty day), the readout following. Without a change the list keeps
    // whatever position the user left it at.
    LaunchedEffect(words) {
        val target = pendingJump
        if (target < 0 || words.isEmpty()) return@LaunchedEffect
        pendingJump = -1
        listState.scrollToItem(target.coerceIn(0, words.size - 1))
    }

    val currentIndex by remember(words.size) {
        derivedStateOf {
            if (words.isEmpty()) {
                0
            } else {
                (listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset / ROW_HEIGHT)
                    .coerceIn(0, words.size - 1)
            }
        }
    }

    Screen {
        // Screen is a Box — the vertical stack needs its own Column, otherwise
        // the list fills the whole area over the tabs/header/scrub bar.
        Column(Modifier.fillMaxSize()) {
        BucketTabs(buckets, tab) { id -> tab = id }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m)
                .padding(top = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${words.size} words",
                color = colors.textTertiary,
                fontSize = FontSize.caption,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (settings.wordsMeaning) EyeIcon else EyeOffIcon,
                contentDescription = "Toggle meanings",
                tint = if (settings.wordsMeaning) colors.accent else colors.textTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable {
                        settingsStore.update(settings.copy(wordsMeaning = !settings.wordsMeaning))
                    },
            )
        }
        Box(Modifier.padding(horizontal = Spacing.m)) {
            ProgressBar(
                value = currentIndex,
                max = maxOf(words.size, 1),
                interactive = true,
                onScrub = { index -> scope.launch { listState.scrollToItem(index) } },
            )
        }
        if (words.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.textTertiary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .background(colors.background),
            ) {
                itemsIndexed(words, key = { _, word -> word.position }) { _, word ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT.dp)
                            .clickable {
                                pushOnce { nav.navigate(WordDetail(bucket = tab, position = word.position)) }
                            }
                            .padding(horizontal = Spacing.m)
                            .drawBehind {
                                drawRect(
                                    colors.separator,
                                    topLeft = Offset(0f, size.height - 0.75f),
                                    size = GSize(size.width, 0.75f),
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${word.position}",
                            color = colors.textTertiary,
                            fontSize = FontSize.caption,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.width(40.dp),
                        )
                        if (word.flagged) {
                            Spacer(
                                Modifier
                                    .padding(end = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.danger),
                            )
                        }
                        Text(
                            word.text,
                            color = colors.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(if (settings.wordsMeaning) 1.1f else 2.1f),
                        )
                        if (settings.wordsMeaning) {
                            Spacer(Modifier.width(Spacing.s))
                            Text(
                                word.meaning,
                                color = colors.textSecondary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

