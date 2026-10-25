package com.time1043.feedah_kt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Thin horizontal position bar. Doubles as a scrubber when interactive:
 * the thumb follows the finger across the whole track and the raw index is
 * reported on release. Consumers enforce their own scrub policy (e.g. the
 * feed snaps back to learned words).
 */
@Composable
fun ProgressBar(
    value: Int,
    max: Int,
    interactive: Boolean,
    onScrub: (Int) -> Unit,
) {
    val colors = LocalFeedahColors.current
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableStateOf(0) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    val thumbRadiusPx = with(density) { 12.dp.toPx() }

    fun indexAt(x: Float): Int {
        if (trackWidthPx <= 0 || max <= 0) return 0
        val ratio = (x / trackWidthPx).coerceIn(0f, 1f)
        return (floor(ratio * max)).toInt().coerceIn(0, max - 1)
    }

    val shownIndex = dragIndex ?: value
    val fraction = if (max > 1) shownIndex / (max - 1f) else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(28.dp)
            .pointerInput(interactive, max) {
                if (!interactive) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragIndex = indexAt(down.position.x)
                    var lastX = down.position.x
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            released = true
                        } else if (change.pressed) {
                            lastX = change.position.x
                            dragIndex = indexAt(lastX)
                        }
                    }
                    val index = indexAt(lastX)
                    dragIndex = null
                    onScrub(index)
                }
            },
    ) {
        // Track + fill sit at the top of the 28dp slot (12dp padding above).
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(y = 12.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.track)
                .onSizeChanged { trackWidthPx = it.width },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent),
            )
        }
        if (dragIndex != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            ((fraction * trackWidthPx) - thumbRadiusPx).roundToInt(),
                            2,
                        )
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent)
                    .size(width = 24.dp, height = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${shownIndex + 1}",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = FontSize.caption,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    // Tabular numerals keep the thumb from wiggling while scrubbing.
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}
