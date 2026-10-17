package io.gastos.app.ui.expenses

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pure rules for swipe-left row actions (Edit, Delete), in dp. Mirrors the PWA's swipeRow.ts. */
object SwipeRowLogic {
    enum class Axis { H, V }

    const val ACTION_WIDTH_DP = 72
    const val OPEN_OFFSET_DP = -2f * ACTION_WIDTH_DP

    /** Movement before a drag counts as a swipe or a scroll, so taps and small wobbles do neither. */
    const val LOCK_SLOP_DP = 10f
    const val RUBBER_DP = 16f

    /** dp per ms; faster than this decides open/closed by direction alone. */
    private const val FLING = 0.5f

    /** H once sideways movement wins, V once vertical does (ties scroll), null before the slop. */
    fun lockAxis(dx: Float, dy: Float): Axis? {
        val ax = abs(dx)
        val ay = abs(dy)
        if (max(ax, ay) <= LOCK_SLOP_DP) return null
        return if (ax > ay) Axis.H else Axis.V
    }

    /** Row offset while dragging: never right of closed, at most a short rubber-band past open. */
    fun dragOffset(start: Float, dx: Float): Float = min(0f, max(OPEN_OFFSET_DP - RUBBER_DP, start + dx))

    /** Where the row rests after release: 0 (closed) or [OPEN_OFFSET_DP]. */
    fun settle(offset: Float, velocityDpPerMs: Float): Float = when {
        velocityDpPerMs < -FLING -> OPEN_OFFSET_DP
        velocityDpPerMs > FLING -> 0f
        offset < OPEN_OFFSET_DP / 2 -> OPEN_OFFSET_DP
        else -> 0f
    }
}
