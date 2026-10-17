package io.gastos.app.ui.expenses

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.gastos.app.ui.expenses.SwipeRowLogic.ACTION_WIDTH_DP
import io.gastos.app.ui.expenses.SwipeRowLogic.Axis
import io.gastos.app.ui.expenses.SwipeRowLogic.OPEN_OFFSET_DP
import io.gastos.app.ui.theme.GastosTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ANIM_MS = 200
private const val PEEK_DELAY_MS = 300L
private const val PEEK_HOLD_MS = 1000L

/**
 * An expense row that slides left to reveal Edit (pen) and Delete (trash). A horizontal drag only takes the
 * gesture once it beats the vertical movement, so the list still scrolls. Tapping an open row closes it.
 */
@Composable
fun SwipeableRow(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    peek: Boolean = false,
    onPeekStart: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = GastosTheme.colors
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val offset = remember { Animatable(0f) } // dp
    var dragging by remember { mutableStateOf(false) }
    var peekJob by remember { mutableStateOf<Job?>(null) }
    val currentOpenChange by rememberUpdatedState(onOpenChange)

    suspend fun moveTo(target: Float) {
        if (reducedMotion) offset.snapTo(target) else offset.animateTo(target, tween(ANIM_MS))
    }

    // Another row opened or the list scrolled: close this one.
    LaunchedEffect(open) {
        if (!dragging && peekJob?.isActive != true) moveTo(if (open) OPEN_OFFSET_DP else 0f)
    }

    LaunchedEffect(peek) {
        if (!peek) return@LaunchedEffect
        onPeekStart()
        peekJob = scope.launch {
            delay(PEEK_DELAY_MS)
            moveTo(OPEN_OFFSET_DP)
            delay(PEEK_HOLD_MS)
            moveTo(0f)
        }
    }

    fun act(action: () -> Unit) {
        currentOpenChange(false)
        action()
    }

    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
            ActionButton(Icons.Outlined.Edit, "Edit", colors.surface2, colors.fg) { act(onEdit) }
            ActionButton(Icons.Outlined.Delete, "Delete", colors.danger, Color.White) { act(onDelete) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(with(density) { offset.value.dp.roundToPx() }, 0) }
                .background(colors.bg)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Edit") { act(onEdit); true },
                        CustomAccessibilityAction("Delete") { act(onDelete); true },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        peekJob?.let { job ->
                            if (job.isActive) {
                                job.cancel()
                                scope.launch { offset.snapTo(0f) }
                            }
                        }
                        val start = offset.value
                        val tracker = VelocityTracker()
                        var total = Offset.Zero
                        var axis: Axis? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            total += change.positionChange()
                            if (axis == null) {
                                axis = SwipeRowLogic.lockAxis(total.x.toDp().value, total.y.toDp().value)
                                if (axis == Axis.V) break // a scroll: leave it to the list
                                if (axis == Axis.H) dragging = true
                            }
                            if (axis == Axis.H) {
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val target = SwipeRowLogic.dragOffset(start, total.x.toDp().value)
                                scope.launch { offset.snapTo(target) }
                            }
                        }
                        if (axis == Axis.H) {
                            dragging = false
                            val vx = tracker.calculateVelocity().x.toDp().value / 1000f // dp per ms
                            val target = SwipeRowLogic.settle(offset.value, vx)
                            scope.launch { moveTo(target) }
                            currentOpenChange(target != 0f)
                        }
                    }
                },
        ) {
            RowClick(open = open, onClose = { onOpenChange(false) }, onClick = onClick, content = content)
        }
    }
}

/** Taps go to [onClick] when closed; on an open row they only close it. */
@Composable
private fun RowClick(open: Boolean, onClose: () -> Unit, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable { if (open) onClose() else onClick() }) { content() }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.width(ACTION_WIDTH_DP.dp).fillMaxHeight().background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}
