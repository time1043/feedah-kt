package com.time1043.feedah_kt.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.time1043.feedah_kt.data.db.MeaningMode
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Spacing

// Word forms render in a fixed 2×6 grid (12 cells). The largest dataset entry
// has 11 forms (word "act"), so 12 cells always fit; empty cells keep the
// layout and card height constant across words.
private const val FORMS_ROWS = 6
private const val FORMS_COLUMNS = 2

private val POS_TAG = Regex("^[a-z]+\\.", RegexOption.IGNORE_CASE)

/**
 * Groups a meaning's "、"-separated senses by part of speech: a sense that
 * starts with a POS tag opens a new line, and any following bare senses join
 * it with "、" so one POS group stays together.
 *   "prep. 为了……、对于……、conj. 因为"
 *     -> ["prep. 为了……、对于……", "conj. 因为"]
 * Returns null when no break is needed: the meaning is short (1-2 senses), or
 * every sense belongs to the same POS group.
 */
internal fun meaningLines(meaning: String): List<String>? {
    val senses = meaning.split('、').map { it.trim() }.filter { it.isNotEmpty() }
    if (senses.size < 3) return null
    val lines = ArrayList<String>()
    for (sense in senses) {
        if (lines.isEmpty() || POS_TAG.containsMatchIn(sense)) lines.add(sense)
        else lines[lines.size - 1] += "、$sense"
    }
    // One group (e.g. "n. 形式、表格、外观") needs no break.
    return if (lines.size > 1) lines else null
}

/**
 * The full-screen word card, split into two independent halves so toggling
 * the meaning never shifts anything:
 *   - top: position number, the word, and a fixed-height slot for the meaning
 *   - bottom: supplementary word forms, shown together with the meaning
 * Tap the word to replay it; tap anywhere else to reveal/hide the meaning;
 * tap the bookmark to flag the word as unfamiliar.
 */
@Composable
fun WordCard(
    position: Int,
    text: String,
    meaning: String,
    forms: List<String>,
    ipa: String? = null,
    flagged: Boolean,
    meaningMode: MeaningMode = MeaningMode.hidden,
    onReplay: () -> Unit,
    onToggleFlagged: () -> Unit,
) {
    val colors = LocalFeedahColors.current
    var revealed by remember { mutableStateOf(meaningMode == MeaningMode.shown) }
    val meaningVisible = meaningMode == MeaningMode.always || revealed
    val toggleMeaning: () -> Unit = {
        if (meaningMode != MeaningMode.always) revealed = !revealed
    }
    val lines = meaningLines(meaning)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(onClick = toggleMeaning)
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "$position",
                color = colors.textSecondary,
                fontSize = FontSize.title,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(bottom = Spacing.m),
            )
            BasicText(
                text,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = FontSize.word, stepSize = 1.sp),
                maxLines = 1,
                style = TextStyle(
                    color = colors.text,
                    fontSize = FontSize.word,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReplay),
            )
            if (!ipa.isNullOrEmpty()) {
                Text(
                    ipa,
                    color = colors.textTertiary,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
            // Reserved slot: keeps number and word anchored while meaning toggles.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.m)
                    .height(110.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (meaningVisible && meaning.isNotEmpty()) {
                    if (lines != null) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            for (line in lines) {
                                Text(line, color = colors.textSecondary, fontSize = 20.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        Text(meaning, color = colors.textSecondary, fontSize = 20.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(onClick = toggleMeaning)
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (meaningVisible && forms.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.l),
                ) {
                    for (row in 0 until FORMS_ROWS) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0 until FORMS_COLUMNS) {
                                val form = forms.getOrNull(row * FORMS_COLUMNS + col)
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .padding(horizontal = Spacing.s)
                                        .padding(bottom = Spacing.s),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (form != null) {
                                        Text(
                                            form,
                                            color = colors.textTertiary,
                                            fontSize = FontSize.caption,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(0.dp))
            }
        }

        // Bookmark pinned near the bottom, above nothing else.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (flagged) BookmarkFilledIcon else BookmarkOutlineIcon,
                contentDescription = if (flagged) "Unflag word" else "Flag word",
                tint = if (flagged) colors.danger else colors.textTertiary,
                modifier = Modifier
                    .width(30.dp)
                    .height(30.dp)
                    .clickable(onClick = onToggleFlagged),
            )
        }
    }
}
