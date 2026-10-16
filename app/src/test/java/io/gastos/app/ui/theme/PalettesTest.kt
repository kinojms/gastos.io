package io.gastos.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {
    @Test fun classicMatchesV1() {
        val p = Palettes.of(ThemeId.CLASSIC, false)
        assertEquals(Color(0xFFEEF1F4), p.bg)
        assertEquals(Color(0xFFE8A317), p.accent)
        val c = GastosColors.from(ThemeId.CLASSIC, false)
        assertEquals(Color(0xFFF3F5F8), c.surface2)
        assertEquals(Color(0xFFE9EDF2), c.key)
        val d = GastosColors.from(ThemeId.CLASSIC, true)
        assertEquals(Color(0xFF151D26), d.surface)
        assertEquals(Color(0xFFEF6B55), d.danger)
    }

    @Test fun nordDarkValues() {
        val p = Palettes.of(ThemeId.NORD, true)
        assertEquals(Color(0xFF2E3440), p.bg)
        assertEquals(Color(0xFF88C0D0), p.accent)
        assertEquals(Color(0xFF2E3440), p.onAccent)
    }

    @Test fun allThemesReadable() {
        for (id in ThemeId.values()) for (dark in listOf(false, true)) {
            val p = Palettes.of(id, dark)
            val a = contrastRatio(p.fg, p.bg)
            val b = contrastRatio(p.onAccent, p.accent)
            assertTrue("$id dark=$dark fg/bg $a", a >= 4.5)
            assertTrue("$id dark=$dark onAccent/accent $b", b >= 4.5)
        }
    }

    @Test fun mutedReadableOnBg() {
        for (id in ThemeId.values()) for (dark in listOf(false, true)) {
            val p = Palettes.of(id, dark)
            val r = contrastRatio(p.muted, p.bg)
            assertTrue("$id dark=$dark muted/bg $r", r >= 4.5)
        }
    }

    @Test fun contrastBlackWhiteIs21() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
    }
}
