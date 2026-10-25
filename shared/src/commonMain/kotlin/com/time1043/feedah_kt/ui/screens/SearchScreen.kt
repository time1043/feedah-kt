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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.time1043.feedah_kt.AppGraph
import com.time1043.feedah_kt.data.db.WordRow
import com.time1043.feedah_kt.logic.SearchReset
import com.time1043.feedah_kt.logic.pushOnce
import com.time1043.feedah_kt.ui.components.ChevronLeftIcon
import com.time1043.feedah_kt.ui.components.CloseIcon
import com.time1043.feedah_kt.ui.navigation.Search
import com.time1043.feedah_kt.ui.navigation.WordDetail
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Word lookup. From home it spans every bucket — results are not deduped, on
 * purpose: the same word in two buckets is worth seeing (and a duplicate
 * inside one bucket exposes a data problem instead of hiding it). From the
 * feed it is pinned to that feed's bucket. Results open the word page pinned
 * to the result's bucket, marked fromSearch so that page's search icon
 * comes back here instead of stacking another search.
 */
@Composable
fun SearchScreen(nav: NavHostController, route: Search) {
    val colors = LocalFeedahColors.current
    val scope = rememberCoroutineScope()
    val pinnedBucket = route.pinnedBucket.orEmpty()
    var scopes by remember { mutableStateOf(emptyList<String>()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<WordRow>()) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The word page's search icon comes back here for a fresh lookup: clear the
    // bar, then raise the keyboard — focus fired mid-transition is swallowed by
    // the screen still animating in, so wait out the pop.
    DisposableEffect(Unit) {
        val off = SearchReset.onSearchReset {
            query = ""
            scope.launch {
                delay(350)
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
        onDispose { off() }
    }

    LaunchedEffect(pinnedBucket) {
        val repo = AppGraph.repo ?: return@LaunchedEffect
        scopes = if (pinnedBucket.isNotEmpty()) {
            listOf(pinnedBucket)
        } else {
            repo.listBuckets().map { it.id }
        }
    }

    LaunchedEffect(query, scopes) {
        val repo = AppGraph.repo
        val trimmed = query.trim()
        if (repo == null || scopes.isEmpty() || trimmed.isEmpty()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        // Scope is the only difference between the two entry points: home and
        // review pass no bucket and search every bucket, while the feed passes
        // its bucket and searches only that one. All match the English word,
        // its inflected forms, AND the Chinese meaning — the meaning column is
        // what makes Chinese queries work, so it must always be included.
        results = scopes.flatMap { repo.searchWords(it, trimmed, matchMeaning = true) }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                ChevronLeftIcon,
                contentDescription = "Back",
                tint = colors.textSecondary,
                modifier = Modifier
                    .padding(6.dp)
                    .clickable { nav.popBackStack() },
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.text, fontSize = FontSize.body),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .focusRequester(focusRequester)
                        .padding(horizontal = Spacing.m, vertical = Spacing.s)
                        .padding(end = 36.dp),
                    visualTransformation = VisualTransformation.None,
                )
                if (query.isNotEmpty()) {
                    Icon(
                        CloseIcon,
                        contentDescription = "Clear search",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .padding(end = Spacing.s)
                            .size(18.dp)
                            .clickable { query = "" },
                    )
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { i, word -> "${word.bucketId}-${word.position}-$i" }) { _, word ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            pushOnce {
                                nav.navigate(
                                    WordDetail(bucket = word.bucketId, position = word.position, fromSearch = true),
                                )
                            }
                        }
                        .padding(horizontal = Spacing.l, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${word.position}",
                        color = colors.textTertiary,
                        fontSize = FontSize.caption,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.width(44.dp),
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
                        fontSize = FontSize.body,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        word.bucketId,
                        color = colors.textTertiary,
                        fontSize = FontSize.caption,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
        }
    }
}
