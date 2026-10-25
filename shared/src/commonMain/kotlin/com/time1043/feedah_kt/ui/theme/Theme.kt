package com.time1043.feedah_kt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.time1043.feedah_kt.data.db.ThemeMode

enum class Scheme { light, dark }

/** The 11-color palette of the original tokens.ts, verbatim values. */
data class FeedahColors(
    val background: Color,
    val surface: Color,
    val surfacePressed: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val success: Color,
    val danger: Color,
    val track: Color,
    val separator: Color,
)

val LightColors = FeedahColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF0F0F3),
    surfacePressed = Color(0xFFE0E1E6),
    text = Color(0xFF000000),
    textSecondary = Color(0xFF60646C),
    textTertiary = Color(0xFF9A9EA6),
    accent = Color(0xFF208AEF),
    success = Color(0xFF3D9A50),
    danger = Color(0xFFE5484D),
    track = Color(0xFFD9D9DE),
    separator = Color(0xFFE5E5EA),
)

val DarkColors = FeedahColors(
    background = Color(0xFF000000),
    surface = Color(0xFF212225),
    surfacePressed = Color(0xFF2E3135),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB0B4BA),
    textTertiary = Color(0xFF7A7E86),
    accent = Color(0xFF4CA0F0),
    success = Color(0xFF5BB96F),
    danger = Color(0xFFF0666B),
    track = Color(0xFF3A3D42),
    separator = Color(0xFF2A2C30),
)

object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

object Radius {
    val s = 8.dp
    val m = 12.dp
    val l = 20.dp
}

object FontSize {
    val caption = 13.sp
    val body = 17.sp
    val title = 28.sp
    val metric = 56.sp

    // Base for feed words. Sized so words up to ~11 characters ("adolescence")
    // all render identically; autosize only shrinks beyond that, which keeps
    // mid-length words uniform instead of shrinking every word.
    val word = 48.sp
}

val LocalFeedahColors = staticCompositionLocalOf { LightColors }
val LocalScheme = staticCompositionLocalOf { Scheme.light }

/** Compose equivalent of the TS rgba() helper used by the heatmap levels. */
fun Color.withAlpha(alpha: Float): Color = copy(alpha = alpha)

/**
 * Mode comes from settings; 'system' follows the OS color scheme. MaterialTheme
 * is mapped onto the custom palette so bundled M3 widgets follow the brand.
 */
@Composable
fun FeedahTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val scheme = when (mode) {
        ThemeMode.system -> if (systemDark) Scheme.dark else Scheme.light
        ThemeMode.light -> Scheme.light
        ThemeMode.dark -> Scheme.dark
    }
    val colors = if (scheme == Scheme.dark) DarkColors else LightColors
    val m3 = if (scheme == Scheme.dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color(0xFFFFFFFF),
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.textSecondary,
            error = colors.danger,
            outline = colors.textTertiary,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color(0xFFFFFFFF),
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.textSecondary,
            error = colors.danger,
            outline = colors.textTertiary,
        )
    }
    CompositionLocalProvider(
        LocalFeedahColors provides colors,
        LocalScheme provides scheme,
    ) {
        MaterialTheme(colorScheme = m3, content = content)
    }
}

/** Platform status-bar/icon appearance; no-op where meaningless. */
@Composable
expect fun StatusBarEffect(darkIcons: Boolean)
