package io.gastos.app.ui.guide

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** The parts of Home the tour can spotlight. */
enum class GuideTarget { NUMPAD, SWIPE_AREA, DESCRIPTION, HISTORY_ICON }

val GuideStep.target: GuideTarget
    get() = when (this) {
        GuideStep.AMOUNT -> GuideTarget.NUMPAD
        GuideStep.METHOD, GuideStep.LABEL -> GuideTarget.SWIPE_AREA
        GuideStep.DESCRIPTION -> GuideTarget.DESCRIPTION
        GuideStep.HISTORY -> GuideTarget.HISTORY_ICON
    }

/**
 * Bounds of each target in root coordinates, reported by [guideTarget] after every layout. A target may be
 * made of several [part]s (the numpad keys and the Enter button); [get] returns their union.
 */
class GuideTargets {
    private val rects = mutableStateMapOf<Pair<GuideTarget, Int>, Rect>()

    fun set(t: GuideTarget, r: Rect, part: Int = 0) {
        if (rects[t to part] != r) rects[t to part] = r
    }

    fun get(t: GuideTarget): Rect? =
        rects.entries.filter { it.key.first == t }.map { it.value }.reduceOrNull { a, b ->
            Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        }
}

/** Reports this element's root bounds as (part of) [target]. */
fun Modifier.guideTarget(targets: GuideTargets, target: GuideTarget, part: Int = 0): Modifier =
    onGloballyPositioned { targets.set(target, it.boundsInRoot(), part) }
