package io.gastos.app.ui.guide

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.ui.home.HomeSwipe
import io.gastos.app.ui.home.HomeSwipeThreshold
import io.gastos.app.ui.theme.GastosTheme
import kotlin.math.min
import kotlin.math.roundToInt

private const val SCRIM_ALPHA = 0.65f
private const val STEP_MS = 250
private const val HAND_LOOP_MS = 1600
private val CutoutPadding = 8.dp

/** Small targets sit close to neighbours (history icon beside the overflow button), so they get less. */
private val SmallTargetPadding = 3.dp
private val CutoutRadius = 16.dp
private val BubbleMargin = 16.dp
private val BubbleMaxWidth = 360.dp

/**
 * Full-screen coach mark for [step]: a dimmed scrim with a lit, rounded hole around the step's target,
 * a text bubble, and (on swipe steps) an animated hand. Every touch is swallowed except the bubble's
 * buttons and, on swipe steps, drags that start inside the hole: those are decided with [HomeSwipe.decide]
 * and reported to [onSwipe] (any axis; the caller filters).
 */
@Composable
fun CoachOverlay(
    step: GuideStep,
    targets: GuideTargets,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onSwipe: (HomeSwipe.Decision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GastosTheme.colors
    val density = LocalDensity.current
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var height by remember { mutableIntStateOf(0) }
    var bubbleHeight by remember { mutableIntStateOf(0) }
    val bubbleBounds = remember { mutableStateOf(Rect.Zero) }

    // Targets report root coordinates; the overlay draws in its own.
    val pad = if (step.target == GuideTarget.HISTORY_ICON) SmallTargetPadding else CutoutPadding
    val cutout = targets.get(step.target)?.let {
        CoachLayout.cutout(it.translate(-origin), with(density) { pad.toPx() })
    }
    val hole = cutout?.let { animateRectAsState(it, tween(STEP_MS), label = "cutout").value }

    val currentStep by rememberUpdatedState(step)
    val currentHole by rememberUpdatedState(hole)
    val currentOnSwipe by rememberUpdatedState(onSwipe)

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                height = it.size.height
            }
            .pointerInput(Unit) {
                val threshold = HomeSwipeThreshold.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The bubble's buttons handle their own touches; consuming them would cancel the press.
                    if (bubbleBounds.value.contains(down.position)) return@awaitEachGesture
                    val swipe = currentStep.swipeAxis != null && currentHole?.contains(down.position) == true
                    down.consume()
                    var total = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { c ->
                            if (swipe && c.id == down.id) total += c.positionChange()
                            c.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    if (swipe) {
                        val d = HomeSwipe.decide(total.x, total.y, threshold)
                        if (d.axis != HomeSwipe.Axis.NONE) currentOnSwipe(d)
                    }
                }
            },
    ) {
        val radius = with(density) { CutoutRadius.toPx() }
        val glowWide = with(density) { 6.dp.toPx() }
        val glowThin = with(density) { 2.dp.toPx() }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .clearAndSetSemantics { },
        ) {
            drawRect(Color.Black.copy(alpha = SCRIM_ALPHA))
            if (hole != null) {
                val r = CornerRadius(radius)
                drawRoundRect(Color.Black, hole.topLeft, hole.size, r, blendMode = BlendMode.Clear)
                drawRoundRect(colors.accent.copy(alpha = 0.35f), hole.topLeft, hole.size, r, style = Stroke(glowWide))
                drawRoundRect(colors.accent, hole.topLeft, hole.size, r, style = Stroke(glowThin))
            }
        }

        val axis = step.swipeAxis
        if (hole != null && axis != null) {
            if (reducedMotion) StaticHand(hole, axis, colors.accent, colors.fg) else AnimatedHand(hole, axis, colors.accent, colors.fg)
        }

        val topInset = WindowInsets.systemBars.getTop(density)
        val bottomInset = WindowInsets.systemBars.getBottom(density)
        val marginPx = with(density) { BubbleMargin.toPx() }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset {
                    // Read during placement, so the measured bubble height applies without another composition.
                    val h = bubbleHeight.toFloat()
                    val top = if (hole == null) {
                        (height - h) / 2f
                    } else {
                        // Lay out inside the area between the system bars so the bubble never sits under them.
                        val usable = (height - topInset - bottomInset).toFloat()
                        CoachLayout.bubbleTop(hole.translate(0f, -topInset.toFloat()), h, usable, marginPx) + topInset
                    }
                    IntOffset(0, top.roundToInt())
                }
                .padding(horizontal = BubbleMargin)
                .widthIn(max = BubbleMaxWidth)
                .fillMaxWidth()
                .onSizeChanged { bubbleHeight = it.height }
                .onGloballyPositioned { bubbleBounds.value = it.boundsInParent() }
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .background(colors.surface, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        ) {
            Crossfade(step, animationSpec = tween(STEP_MS), label = "bubble") { shown ->
                // The fading-out copy keeps its buttons for a moment; only the live step's buttons act.
                Bubble(
                    step = shown,
                    onNext = { if (shown == currentStep) onNext() },
                    onSkip = { if (shown == currentStep) onSkip() },
                )
            }
        }
    }
}

@Composable
private fun Bubble(step: GuideStep, onNext: () -> Unit, onSkip: () -> Unit) {
    val colors = GastosTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(
            step.text,
            color = colors.fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${step.number} of $GUIDE_TOTAL", color = colors.muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSkip) { Text("Skip", color = colors.fg) }
            if (step.swipeAxis == null) {
                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) { Text(if (step.isLast) "Done" else "Next", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Unit vector of the demonstrated swipe: leftward for the payment method, upward for the label. */
private fun direction(axis: HomeSwipe.Axis): Offset =
    if (axis == HomeSwipe.Axis.LABEL) Offset(0f, -1f) else Offset(-1f, 0f)

/** Half the distance the hand travels, kept inside the hole. */
private fun DrawScope.reach(hole: Rect, axis: HomeSwipe.Axis): Float {
    val span = if (axis == HomeSwipe.Axis.LABEL) hole.height else hole.width
    return min(span * 0.3f, 120.dp.toPx())
}

/** A fingertip sliding across the hole on a loop, with a fading trail behind it. */
@Composable
private fun AnimatedHand(hole: Rect, axis: HomeSwipe.Axis, accent: Color, ink: Color) {
    val transition = rememberInfiniteTransition(label = "hand")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(HAND_LOOP_MS, easing = LinearEasing)),
        label = "handProgress",
    )
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        // 0..0.7: travel (eased); 0.7..1: rest, invisible.
        val p = progress
        if (p > 0.7f) return@Canvas
        val t = FastOutSlowInEasing.transform(p / 0.7f)
        val alpha = when {
            t < 0.15f -> t / 0.15f
            t > 0.85f -> (1f - t) / 0.15f
            else -> 1f
        }.coerceIn(0f, 1f)
        val dir = direction(axis)
        val reach = reach(hole, axis)
        val start = hole.center - dir * reach
        val tip = start + dir * (2f * reach * t)
        for (i in 4 downTo 1) {
            val back = tip - dir * (i * 10.dp.toPx() * t)
            drawCircle(ink.copy(alpha = alpha * 0.12f * (5 - i)), radius = (16 - i * 2).dp.toPx(), center = back)
        }
        drawFingertip(tip, accent, alpha)
    }
}

/** Reduced motion: the fingertip rests in the middle with arrows both ways along the axis. */
@Composable
private fun StaticHand(hole: Rect, axis: HomeSwipe.Axis, accent: Color, ink: Color) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val c = hole.center
        drawFingertip(c, accent, 1f)
        val dir = direction(axis)
        val gap = 34.dp.toPx()
        drawChevron(c + dir * gap, dir, ink)
        drawChevron(c - dir * gap, -dir, ink)
    }
}

private fun DrawScope.drawFingertip(center: Offset, accent: Color, alpha: Float) {
    drawCircle(Color.White.copy(alpha = 0.92f * alpha), radius = 16.dp.toPx(), center = center)
    drawCircle(accent.copy(alpha = alpha), radius = 16.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
}

private fun DrawScope.drawChevron(tip: Offset, dir: Offset, color: Color) {
    val size = 9.dp.toPx()
    val normal = Offset(-dir.y, dir.x)
    val back = tip - dir * size
    val path = Path().apply {
        moveTo(back.x + normal.x * size, back.y + normal.y * size)
        lineTo(tip.x, tip.y)
        lineTo(back.x - normal.x * size, back.y - normal.y * size)
    }
    drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
