package io.gastos.app.ui.home

import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Pure rules for Home's two-axis swipe: horizontal picks the payment method, vertical picks the label. */
object HomeSwipe {
    enum class Axis { NONE, METHOD, LABEL }

    data class Decision(val axis: Axis, val delta: Int)

    private val NONE = Decision(Axis.NONE, 0)

    /**
     * Decides a finished drag of ([dx], [dy]) pixels. The dominant axis wins and must reach [thresholdPx],
     * so taps never count. Left (dx < 0) is the next method, up (dy < 0) is the next label.
     */
    fun decide(dx: Float, dy: Float, thresholdPx: Float): Decision {
        val ax = abs(dx)
        val ay = abs(dy)
        return when {
            maxOf(ax, ay) < thresholdPx -> NONE
            ax > ay -> Decision(Axis.METHOD, if (dx < 0) +1 else -1)
            else -> Decision(Axis.LABEL, if (dy < 0) +1 else -1)
        }
    }

    /** Index [delta] steps from [currentIndex], wrapping around [items]; 0 when [items] is empty. */
    fun <T> step(items: List<T>, currentIndex: Int, delta: Int): Int =
        if (items.isEmpty()) 0 else Math.floorMod(currentIndex + delta, items.size)
}

/** Minimum drag on the dominant axis for a swipe to count; shared by Home and the guided tour. */
val HomeSwipeThreshold = 48.dp
