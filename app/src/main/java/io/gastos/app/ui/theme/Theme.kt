package io.gastos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Immutable
data class GastosColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val fg: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val key: Color,
    val danger: Color,
    val onAccent: Color,
) {
    companion object {
        fun from(id: ThemeId, dark: Boolean): GastosColors {
            if (id == ThemeId.CLASSIC) return if (dark) DarkGastosColors else LightGastosColors
            val p = Palettes.of(id, dark)
            return GastosColors(
                bg = p.bg, surface = p.surface,
                surface2 = lerp(p.bg, p.surface, 0.5f),
                fg = p.fg, muted = p.muted, line = p.line, accent = p.accent,
                key = p.surface,
                danger = if (dark) Color(0xFFEF6B55) else Color(0xFFC2412D),
                onAccent = p.onAccent,
            )
        }
    }
}

val LightGastosColors = GastosColors(
    bg = Color(0xFFEEF1F4),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF3F5F8),
    fg = Color(0xFF16202B),
    muted = Color(0xFF5D6B7A),
    line = Color(0xFFDDE3EA),
    accent = Color(0xFFE8A317),
    key = Color(0xFFE9EDF2),
    danger = Color(0xFFC2412D),
    onAccent = Color(0xFF1C1300),
)

val DarkGastosColors = GastosColors(
    bg = Color(0xFF0B1016),
    surface = Color(0xFF151D26),
    surface2 = Color(0xFF1C2632),
    fg = Color(0xFFE8EDF2),
    muted = Color(0xFF92A0AE),
    line = Color(0xFF2A3542),
    accent = Color(0xFFF2B33A),
    key = Color(0xFF222D3A),
    danger = Color(0xFFEF6B55),
    onAccent = Color(0xFF1C1300),
)

val LocalGastosColors = staticCompositionLocalOf { LightGastosColors }

/** Access extra tokens via `GastosTheme.colors` inside a [GastosTheme] content block. */
object GastosTheme {
    val colors: GastosColors
        @Composable get() = LocalGastosColors.current
}

@Composable
fun GastosTheme(themeId: ThemeId = ThemeId.CLASSIC, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = GastosColors.from(themeId, dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.bg, onBackground = c.fg,
            surface = c.surface, onSurface = c.fg,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2,
            inverseSurface = c.fg, inverseOnSurface = c.bg, inversePrimary = c.accent,
            outline = c.line, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.bg, onBackground = c.fg,
            surface = c.surface, onSurface = c.fg,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2,
            inverseSurface = c.fg, inverseOnSurface = c.bg, inversePrimary = c.accent,
            outline = c.line, error = c.danger,
        )
    }
    CompositionLocalProvider(LocalGastosColors provides c) {
        MaterialTheme(colorScheme = scheme) {
            Surface(color = c.bg, contentColor = c.fg, content = content)
        }
    }
}
