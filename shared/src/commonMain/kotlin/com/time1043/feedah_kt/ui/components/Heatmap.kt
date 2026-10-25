package com.time1043.feedah_kt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.withAlpha
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

/** GitHub-style calendar heatmap for one year with month/weekday labels and tappable cells. */
object HeatmapLayout {
    const val WEEKDAYS = 7
    val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val LABEL_ROWS = listOf(0, 1, 3, 5) // Sun, Mon, Wed, Fri — like GitHub
}

/**
 * Pure week grid for a year: columns of 7 days, Sunday-first, with leading
 * (and trailing) nulls. kotlinx-datetime local-date math keeps it DST-safe.
 */
internal fun heatmapWeeks(year: Int): List<List<String?>> {
    val cursor = LocalDate(year, 1, 1)
    val end = LocalDate(year, 12, 31)
    // Original getDay(): Sunday=0..Saturday=6 → days to pad before the grid.
    val sundayIndex = (cursor.dayOfWeek.ordinal + 1) % 7
    val weeks = ArrayList<List<String?>>()
    var week = ArrayList<String?>()
    repeat(sundayIndex) { week.add(null) }
    var current: LocalDate = cursor
    while (current <= end) {
        week.add(current.toString())
        if (week.size == HeatmapLayout.WEEKDAYS) {
            weeks.add(week)
            week = ArrayList()
        }
        current = LocalDate.fromEpochDays(current.toEpochDays() + 1)
    }
    if (week.isNotEmpty()) weeks.add(week)
    return weeks
}

/** Label a week column when its month differs from the previous labeled one. */
internal fun heatmapMonthLabels(weeks: List<List<String?>>): List<String?> {
    val labels = ArrayList<String?>(weeks.size)
    var lastMonth = -1
    for (days in weeks) {
        val first = days.firstOrNull { it != null }
        if (first == null) {
            labels.add(null)
            continue
        }
        val month = first.substring(5, 7).toInt() - 1
        if (month != lastMonth) {
            labels.add(HeatmapLayout.MONTHS[month])
            lastMonth = month
        } else {
            labels.add(null)
        }
    }
    return labels
}

/** `<=` threshold ladder, level 0 = empty (also paints the track color). */
internal fun heatmapLevel(value: Long?, thresholds: List<Long>): Int {
    if (value == null || value <= 0) return 0
    if (value <= thresholds[0]) return 1
    if (value <= thresholds[1]) return 2
    if (value <= thresholds[2]) return 3
    return 4
}

@Composable
fun Heatmap(
    year: Int,
    values: Map<String, Long>,
    thresholds: List<Long>,
    selectedDay: String? = null,
    onSelectDay: ((String) -> Unit)? = null,
    cellSize: Float = 12f,
) {
    val colors = LocalFeedahColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = 2f
    val cellPx = with(density) { cellSize.dp.toPx() }
    val gutterPx = with(density) { 30.dp.toPx() }
    val gapPx = with(density) { gap.dp.toPx() }
    val step = cellPx + gapPx

    val weeks = remember(year) { heatmapWeeks(year) }
    val monthLabels = remember(weeks) { heatmapMonthLabels(weeks) }

    val levelColors = listOf(
        colors.track,
        colors.success.withAlpha(0.35f),
        colors.success.withAlpha(0.6f),
        colors.success.withAlpha(0.8f),
        colors.success,
    )

    val gridWidth = weeks.size * step - gapPx
    val gridHeight = 7 * step - gapPx

    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
    ) {
        // Month labels row.
        Box(Modifier.width(with(density) { (gutterPx + gridWidth).toDp() }).height(14.dp)) {
            for ((index, label) in monthLabels.withIndex()) {
                if (label != null) {
                    Text(
                        label,
                        color = colors.textTertiary,
                        fontSize = 9.sp,
                        modifier = Modifier.offset(
                            x = with(density) { (gutterPx + index * step).toDp() },
                        ),
                    )
                }
            }
        }
        Row {
            // Weekday gutter.
            Column(
                Modifier.width(with(density) { gutterPx.toDp() }),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(with(density) { gapPx.toDp() }),
            ) {
                for (row in 0 until HeatmapLayout.WEEKDAYS) {
                    Box(Modifier.height(with(density) { cellPx.toDp() }), contentAlignment = Alignment.CenterEnd) {
                        if (row in HeatmapLayout.LABEL_ROWS) {
                            Text(
                                HeatmapLayout.WEEKDAY_LABELS[row],
                                color = colors.textTertiary,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            // The grid itself.
            Canvas(
                Modifier
                    .width(with(density) { gridWidth.toDp() })
                    .height(with(density) { gridHeight.toDp() })
                    .pointerInput(year, weeks, selectedDay) {
                        detectTapGestures { offset ->
                            val weekIndex = (offset.x / step).toInt()
                            val dayIndex = (offset.y / step).toInt()
                            if (weekIndex in weeks.indices) {
                                val day = weeks[weekIndex].getOrNull(dayIndex)
                                if (day != null) onSelectDay?.invoke(day)
                            }
                        }
                    },
            ) {
                val corner = CornerRadius(with(density) { 2.dp.toPx() })
                for ((weekIndex, days) in weeks.withIndex()) {
                    for ((dayIndex, day) in days.withIndex()) {
                        if (day == null) continue
                        val selected = day == selectedDay
                        val topLeft = Offset(weekIndex * step, dayIndex * step)
                        drawRoundRect(
                            color = levelColors[heatmapLevel(values[day], thresholds)],
                            topLeft = topLeft,
                            size = Size(cellPx, cellPx),
                            cornerRadius = corner,
                        )
                        if (selected) {
                            drawRoundRect(
                                color = colors.accent,
                                topLeft = topLeft,
                                size = Size(cellPx, cellPx),
                                cornerRadius = corner,
                                style = Stroke(with(density) { 1.5.dp.toPx() }),
                            )
                        }
                    }
                }
            }
        }
    }
}
