package com.time1043.feedah_kt.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-built stand-ins for the Ionicons the original used (@expo/vector-icons
 * has no multiplatform artifact). Stroke-based; Icon() tints the whole raster
 * so the placeholder black paints are overridden at draw time.
 */

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}

private fun ImageVector.Builder.fillPath(block: PathBuilder.() -> Unit) {
    path(fill = SolidColor(Color.Black), pathBuilder = block)
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    close()
}

val HomeIcon: ImageVector = icon("Home") {
    strokePath {
        moveTo(3.5f, 11f)
        lineTo(12f, 3.5f)
        lineTo(20.5f, 11f)
        moveTo(6.3f, 9.8f)
        verticalLineTo(20f)
        horizontalLineTo(17.7f)
        verticalLineTo(9.8f)
    }
}

val BookIcon: ImageVector = icon("Book") {
    strokePath {
        // Closed book: rounded right end of the cover, then the page block.
        moveTo(6.5f, 3.5f)
        horizontalLineTo(14.8f)
        arcTo(3.3f, 3.3f, 0f, false, true, 14.8f, 10.1f)
        horizontalLineTo(6.5f)
        close()
        moveTo(6.5f, 10.1f)
        verticalLineTo(18.4f)
        arcTo(2.6f, 2.6f, 0f, false, false, 9.1f, 21f)
        horizontalLineTo(17.5f)
        verticalLineTo(6.8f)
    }
}

val ChartIcon: ImageVector = icon("Chart") {
    strokePath {
        moveTo(5.5f, 20f)
        verticalLineTo(12.5f)
        moveTo(12f, 20f)
        verticalLineTo(4.5f)
        moveTo(18.5f, 20f)
        verticalLineTo(8.5f)
        moveTo(3.5f, 20.5f)
        horizontalLineTo(20.5f)
    }
}

val GearIcon: ImageVector = icon("Gear") {
    strokePath {
        circle(12f, 12f, 3.1f)
        for (i in 0 until 8) {
            val angle = (kotlin.math.PI / 4 * i)
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            moveTo(12f + 5.2f * cos, 12f + 5.2f * sin)
            lineTo(12f + 8.1f * cos, 12f + 8.1f * sin)
        }
    }
}

val BookmarkFilledIcon: ImageVector = icon("BookmarkFilled") {
    fillPath {
        moveTo(17f, 3f)
        horizontalLineTo(7f)
        curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
        verticalLineTo(21f)
        lineToRelative(7f, -3f)
        lineToRelative(7f, 3f)
        verticalLineTo(5f)
        curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
        close()
    }
}

val BookmarkOutlineIcon: ImageVector = icon("BookmarkOutline") {
    fillPath {
        moveTo(17f, 3f)
        horizontalLineTo(7f)
        curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
        verticalLineTo(21f)
        lineToRelative(7f, -3f)
        lineToRelative(7f, 3f)
        verticalLineTo(5f)
        curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
        close()
        moveTo(17f, 18f)
        lineToRelative(-5f, -2.18f)
        lineTo(7f, 18f)
        verticalLineTo(5f)
        horizontalLineTo(17f)
        verticalLineTo(18f)
        close()
    }
}

val EyeIcon: ImageVector = icon("Eye") {
    strokePath {
        moveTo(2.5f, 12f)
        curveTo(4.5f, 7.5f, 8f, 5.5f, 12f, 5.5f)
        curveTo(16f, 5.5f, 19.5f, 7.5f, 21.5f, 12f)
        curveTo(19.5f, 16.5f, 16f, 18.5f, 12f, 18.5f)
        curveTo(8f, 18.5f, 4.5f, 16.5f, 2.5f, 12f)
        close()
        circle(12f, 12f, 3.1f)
    }
}

val EyeOffIcon: ImageVector = icon("EyeOff") {
    strokePath {
        moveTo(4f, 4f)
        lineTo(20f, 20f)
        moveTo(9.9f, 5.9f)
        curveTo(10.6f, 5.75f, 11.3f, 5.67f, 12f, 5.67f)
        curveTo(16f, 5.67f, 19.5f, 7.67f, 21.5f, 12f)
        curveTo(20.7f, 13.7f, 19.6f, 15.1f, 18.3f, 16.2f)
        moveTo(6.1f, 6.9f)
        curveTo(4.6f, 8.2f, 3.3f, 9.9f, 2.5f, 12f)
        curveTo(4.5f, 16.3f, 8f, 18.3f, 12f, 18.3f)
        curveTo(13.2f, 18.3f, 14.3f, 18.1f, 15.4f, 17.8f)
        moveTo(9.9f, 9.9f)
        curveTo(10.5f, 9.3f, 11.2f, 9f, 12f, 9f)
        curveTo(13.7f, 9f, 15f, 10.3f, 15f, 12f)
        curveTo(15f, 12.8f, 14.7f, 13.5f, 14.1f, 14.1f)
    }
}

val SearchIcon: ImageVector = icon("Search") {
    strokePath {
        circle(11f, 11f, 6.5f)
        moveTo(15.8f, 15.8f)
        lineTo(21f, 21f)
    }
}

val CloseIcon: ImageVector = icon("Close") {
    strokePath {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }
}

val ChevronDownIcon: ImageVector = icon("ChevronDown") {
    strokePath {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }
}
