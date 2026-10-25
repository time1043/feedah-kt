package com.time1043.feedah_kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.time1043.feedah_kt.AppGraph
import com.time1043.feedah_kt.data.db.BucketInfo
import com.time1043.feedah_kt.data.db.FlagCounts
import com.time1043.feedah_kt.data.db.Progress
import com.time1043.feedah_kt.logic.pushOnce
import com.time1043.feedah_kt.ui.components.BucketTabs
import com.time1043.feedah_kt.ui.components.Screen
import com.time1043.feedah_kt.ui.components.SearchIcon
import com.time1043.feedah_kt.ui.navigation.Feed
import com.time1043.feedah_kt.ui.navigation.Review
import com.time1043.feedah_kt.ui.navigation.Search
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Radius
import com.time1043.feedah_kt.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(nav: NavHostController) {
    val colors = LocalFeedahColors.current
    val settingsStore = AppGraph.settings!!
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Single source of truth: the same value feed, search and the word page
    // read. Home must never derive its own copy from the database.
    val activeId = settings.activeBucketId

    var buckets by remember { mutableStateOf(emptyList<BucketInfo>()) }
    var progress by remember { mutableStateOf<Progress?>(null) }
    var wordCount by remember { mutableStateOf(0) }
    var flagCounts by remember { mutableStateOf(FlagCounts(0, 0)) }
    var flaggedTotal by remember { mutableStateOf(0) }

    fun load() {
        scope.launch {
            val repo = AppGraph.repo ?: return@launch
            val list = repo.listBuckets()
            if (list.isEmpty()) return@launch // DB not ready yet; the next focus retries
            buckets = list
            // The stored active bucket must be a real bucket; otherwise fall
            // back to the first one (2050) and persist the correction.
            val valid = if (list.any { it.id == activeId }) activeId else list[0].id
            if (valid != activeId) {
                settingsStore.update(settings.copy(activeBucketId = valid))
            }
            progress = repo.getProgress(valid)
            wordCount = repo.getWordCount(valid)
            flagCounts = repo.getRoundFlagCounts(valid)
            flaggedTotal = repo.countFlaggedWords(valid)
        }
    }

    // Runs on mount, whenever the active bucket changes, and on every focus.
    LaunchedEffect(activeId) { load() }
    LifecycleResumeEffect(Unit) {
        load()
        onPauseOrDispose { }
    }

    val round = progress?.round ?: 1
    val pointer = progress?.pointer ?: 0
    val started = pointer > 0 || round > 1

    Screen {
        Column(Modifier.fillMaxSize()) {
        BucketTabs(buckets, activeId) { id ->
            if (id != activeId) {
                // Persisted here; the activeId change re-runs the load above.
                settingsStore.update(settings.copy(activeBucketId = id))
            }
        }

        Row(
            Modifier
                .padding(horizontal = Spacing.m, vertical = 0.dp)
                .padding(top = Spacing.m)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.l))
                .background(colors.surface)
                .clickable { nav.navigate(Search()) }
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                SearchIcon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            Text("Search words", color = colors.textTertiary, fontSize = FontSize.body)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
        ) {
            Text("Round $round", color = colors.textSecondary, fontSize = FontSize.body)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$pointer",
                    color = colors.text,
                    fontSize = FontSize.metric,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text(
                    " / $wordCount",
                    color = colors.textTertiary,
                    fontSize = FontSize.title,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                FlagGroup(
                    count = flagCounts.green,
                    dotColor = colors.success,
                    countColor = colors.textSecondary,
                    onClick = null,
                )
                FlagGroup(
                    count = flagCounts.red,
                    dotColor = colors.danger,
                    countColor = if (flagCounts.red > 0) colors.danger else colors.textSecondary,
                    onClick = if (flagCounts.red > 0) {
                        { nav.navigate(Review(bucket = activeId, round = round)) }
                    } else {
                        null
                    },
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.l))
                    .background(colors.accent)
                    .clickable { pushOnce { nav.navigate(Feed(bucket = activeId)) } }
                    .padding(horizontal = Spacing.xl, vertical = Spacing.m),
            ) {
                Text(
                    if (started) "Continue" else "Start",
                    color = Color.White,
                    fontSize = FontSize.body,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.l))
                    .clickable(enabled = flaggedTotal > 0) { nav.navigate(Review(bucket = activeId)) }
                    .border(1.5.dp, colors.accent, RoundedCornerShape(Radius.l))
                    .padding(horizontal = Spacing.l, vertical = Spacing.s)
                    .alpha(if (flaggedTotal > 0) 1f else 0.4f),
            ) {
                Text("Review · $flaggedTotal", color = colors.accent, fontSize = FontSize.caption, fontWeight = FontWeight.SemiBold)
            }
        }
        }
    }
}

@Composable
private fun FlagGroup(
    count: Int,
    dotColor: Color,
    countColor: Color,
    onClick: (() -> Unit)?,
) {
    val colors = LocalFeedahColors.current
    Row(
        Modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
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
