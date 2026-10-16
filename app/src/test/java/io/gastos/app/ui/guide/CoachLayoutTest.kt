package io.gastos.app.ui.guide

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachLayoutTest {
    private val margin = 16f

    @Test fun bubbleGoesBelowWhenRoom() {
        val cutout = Rect(0f, 200f, 1000f, 400f)
        assertEquals(400f + margin, CoachLayout.bubbleTop(cutout, 150f, 2000f, margin), 0.001f)
    }

    @Test fun bubbleGoesAboveNearBottom() {
        val cutout = Rect(0f, 1700f, 1000f, 1950f)
        assertEquals(1700f - margin - 150f, CoachLayout.bubbleTop(cutout, 150f, 2000f, margin), 0.001f)
    }

    @Test fun bubbleClampedOnScreen() {
        val cutout = Rect(0f, 50f, 1000f, 1980f)
        val top = CoachLayout.bubbleTop(cutout, 150f, 2000f, margin)
        assertTrue("top=$top", top >= margin && top <= 2000f - 150f - margin)
    }

    @Test fun cutoutAddsPadding() {
        assertEquals(Rect(92f, 192f, 308f, 408f), CoachLayout.cutout(Rect(100f, 200f, 300f, 400f), 8f))
    }

    @Test fun stepsMapToTargets() {
        assertEquals(GuideTarget.NUMPAD, GuideStep.AMOUNT.target)
        assertEquals(GuideTarget.SWIPE_AREA, GuideStep.METHOD.target)
        assertEquals(GuideTarget.SWIPE_AREA, GuideStep.LABEL.target)
        assertEquals(GuideTarget.DESCRIPTION, GuideStep.DESCRIPTION.target)
        assertEquals(GuideTarget.HISTORY_ICON, GuideStep.HISTORY.target)
    }

    @Test fun targetPartsAreUnioned() {
        val t = GuideTargets()
        t.set(GuideTarget.NUMPAD, Rect(10f, 100f, 200f, 300f), part = 0)
        t.set(GuideTarget.NUMPAD, Rect(20f, 310f, 190f, 360f), part = 1)
        assertEquals(Rect(10f, 100f, 200f, 360f), t.get(GuideTarget.NUMPAD))
        assertEquals(null, t.get(GuideTarget.DESCRIPTION))
    }
}
