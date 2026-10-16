package io.gastos.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

enum class ThemeId(val displayName: String) {
    CLASSIC("Classic"),
    SAND("Sand"),
    NORD("Nord"),
    MOCHA("Mocha"),
    FOREST("Forest"),
}

data class Palette(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
)

object Palettes {
    private fun p(bg: Long, surface: Long, fg: Long, muted: Long, line: Long, accent: Long, onAccent: Long) =
        Palette(Color(bg), Color(surface), Color(fg), Color(muted), Color(line), Color(accent), Color(onAccent))

    fun of(id: ThemeId, dark: Boolean): Palette = when (id) {
        ThemeId.CLASSIC ->
            if (dark) p(0xFF0B1016, 0xFF1C2632, 0xFFE8EDF2, 0xFF92A0AE, 0xFF2A3542, 0xFFF2B33A, 0xFF1C1300)
            else p(0xFFEEF1F4, 0xFFFFFFFF, 0xFF16202B, 0xFF5D6B7A, 0xFFDDE3EA, 0xFFE8A317, 0xFF1C1300)
        ThemeId.SAND ->
            if (dark) p(0xFF002B36, 0xFF073642, 0xFFEEE8D5, 0xFF93A1A1, 0xFF0E4B5A, 0xFFB58900, 0xFF002B36)
            else p(0xFFFDF6E3, 0xFFFFFBF0, 0xFF073642, 0xFF5F747C, 0xFFE8E1CC, 0xFF8F6C00, 0xFFFDF6E3)
        ThemeId.NORD ->
            if (dark) p(0xFF2E3440, 0xFF3B4252, 0xFFECEFF4, 0xFFD8DEE9, 0xFF434C5E, 0xFF88C0D0, 0xFF2E3440)
            else p(0xFFECEFF4, 0xFFFFFFFF, 0xFF2E3440, 0xFF4C566A, 0xFFD8DEE9, 0xFF5378A3, 0xFFFFFFFF)
        ThemeId.MOCHA ->
            if (dark) p(0xFF1E1E2E, 0xFF313244, 0xFFCDD6F4, 0xFFA6ADC8, 0xFF45475A, 0xFFCBA6F7, 0xFF1E1E2E)
            else p(0xFFEFF1F5, 0xFFFFFFFF, 0xFF4C4F69, 0xFF696C82, 0xFFDCE0E8, 0xFF8839EF, 0xFFFFFFFF)
        ThemeId.FOREST ->
            if (dark) p(0xFF282828, 0xFF32302F, 0xFFEBDBB2, 0xFFA89984, 0xFF504945, 0xFFB8BB26, 0xFF282828)
            else p(0xFFFBF1C7, 0xFFF9F5D7, 0xFF3C3836, 0xFF776A60, 0xFFEBDBB2, 0xFF74700D, 0xFFFBF1C7)
    }
}

/** WCAG 2.x contrast ratio between two opaque colors. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
