package com.time1043.feedah_kt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

enum class RoundWordStatus { green, red, gray }

/**
 * Horizontal timeline of a round. Words are aggregated into one-pixel columns
 * (red wins over green over gray): a 2000-word bucket on a ~360dp bar cannot
 * be resolved per word, and one rect per pixel keeps the draw cheap and every
 * red word visible.
 */
@Composable
fun RoundBar(statuses: List<RoundWordStatus>) {
    val colors = LocalFeedahColors.current
    var widthPx by remember { mutableStateOf(0) }

    val columns = remember(statuses, widthPx) { aggregateRoundColumns(statuses, widthPx) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .onSizeChanged { widthPx = it.width },
    ) {
        for ((index, status) in columns.withIndex()) {
            drawRect(
                color = when (status) {
                    RoundWordStatus.red -> colors.danger
                    RoundWordStatus.green -> colors.success
                    RoundWordStatus.gray -> colors.track
                },
                topLeft = Offset(index.toFloat(), 0f),
                size = Size(1f, size.height),
            )
        }
    }
}

/** Pure pixel aggregation — jvmTest locks the red-over-green-over-gray rule. */
internal fun aggregateRoundColumns(
    statuses: List<RoundWordStatus>,
    widthPx: Int,
): List<RoundWordStatus> {
    if (widthPx <= 0 || statuses.isEmpty()) return emptyList()
    val pixelCount = min(statuses.size, widthPx)
    val groupSize = statuses.size.toDouble() / pixelCount
    return List(pixelCount) { pixel ->
        val start = floor(pixel * groupSize).toInt()
        val end = if (pixel == pixelCount - 1) {
            statuses.size
        } else {
            min(statuses.size, floor((pixel + 1) * groupSize).toInt())
        }
        var column = RoundWordStatus.gray
        for (i in start until end) {
            if (statuses[i] == RoundWordStatus.red) {
                column = RoundWordStatus.red
                break
            }
            if (statuses[i] == RoundWordStatus.green) {
                column = RoundWordStatus.green
            }
        }
        column
    }
}
