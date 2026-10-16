package io.gastos.app.ui.guide

import androidx.compose.ui.geometry.Rect

/** Pure geometry for the coach-mark overlay: the spotlight hole and where the text bubble goes. */
object CoachLayout {
    /** The spotlight hole: [target] grown by [paddingPx] on every side. */
    fun cutout(target: Rect, paddingPx: Float): Rect = target.inflate(paddingPx)

    /**
     * Top of the bubble: below the cutout when it fits, otherwise above it when that fits, otherwise on
     * whichever side has more room. Always clamped to [marginPx, screenHeight - bubbleHeight - marginPx].
     */
    fun bubbleTop(cutout: Rect, bubbleHeight: Float, screenHeight: Float, marginPx: Float): Float {
        val below = cutout.bottom + marginPx
        val above = cutout.top - marginPx - bubbleHeight
        val fitsBelow = below + bubbleHeight + marginPx <= screenHeight
        val fitsAbove = above >= marginPx
        val top = when {
            fitsBelow -> below
            fitsAbove -> above
            screenHeight - cutout.bottom >= cutout.top -> below
            else -> above
        }
        val max = screenHeight - bubbleHeight - marginPx
        return if (max < marginPx) marginPx else top.coerceIn(marginPx, max)
    }
}
