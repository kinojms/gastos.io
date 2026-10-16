package io.gastos.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gastos.app.domain.AmountInput
import io.gastos.app.domain.ExpenseText
import io.gastos.app.domain.Money
import io.gastos.app.ui.components.Numpad
import io.gastos.app.ui.components.rememberVoiceInput
import io.gastos.app.ui.guide.CoachOverlay
import io.gastos.app.ui.guide.GuideController
import io.gastos.app.ui.guide.GuideTarget
import io.gastos.app.ui.guide.GuideTargets
import io.gastos.app.ui.guide.guideTarget
import io.gastos.app.ui.theme.GastosTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
/** Pause after a tour swipe before the next step, so the user sees what changed. */
private const val TOUR_SWIPE_ADVANCE_MS = 400L
private const val TINT_LIGHT = 0.20f
private const val TINT_DARK = 0.26f

/** Where the description box sits, so a tap on it is not treated as "outside". */
private class FieldBounds {
    var root: LayoutCoordinates? = null
    var field: LayoutCoordinates? = null

    fun contains(pos: Offset): Boolean {
        val r = root?.takeIf { it.isAttached } ?: return false
        val f = field?.takeIf { it.isAttached } ?: return false
        return r.localBoundingBoxOf(f).contains(pos)
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    guide: GuideController,
    onOpenExpenses: () -> Unit,
    onOpenLabels: () -> Unit,
    onOpenMethods: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val voiceLanguage by vm.voiceLanguage.collectAsStateWithLifecycle()
    val colors = GastosTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val bounds = remember { FieldBounds() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val targets = remember { GuideTargets() }
    val tourStep by guide.step.collectAsStateWithLifecycle()
    // While the tour runs, only its swipe, Next/Done and Skip do anything.
    val touring = tourStep != null
    BackHandler(enabled = touring) { guide.skip() }
    // TalkBack must not reach the dimmed controls; only the swipe area (its actions) on swipe steps stays.
    val hidden: Modifier = if (touring) Modifier.clearAndSetSemantics { } else Modifier
    val swipeHidden: Modifier = if (touring && tourStep?.swipeAxis == null) Modifier.clearAndSetSemantics { } else Modifier

    LaunchedEffect(Unit) { guide.startIfUnseen() }
    LaunchedEffect(touring) {
        if (touring) {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    LaunchedEffect(vm) {
        vm.toasts.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(msg) }
        }
    }

    val methodCount = state.methods.size
    val labelCount = state.labels.size
    val onMethod = { delta: Int ->
        if (methodCount > 1) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            vm.swipeMethod(delta)
        }
    }
    val onLabel = { delta: Int ->
        if (labelCount > 1) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            vm.swipeLabel(delta)
        }
    }

    /**
     * Every swipe (touch, accessibility action, or the tour overlay) lands here. During the tour only the
     * step's axis counts, and it completes the step even when there is nothing to change.
     */
    val onSwipe = { axis: HomeSwipe.Axis, delta: Int ->
        val step = guide.step.value
        if (step == null || step.swipeAxis == axis) {
            when (axis) {
                HomeSwipe.Axis.METHOD -> onMethod(delta)
                HomeSwipe.Axis.LABEL -> onLabel(delta)
                HomeSwipe.Axis.NONE -> Unit
            }
            if (step != null) {
                scope.launch {
                    delay(TOUR_SWIPE_ADVANCE_MS)
                    guide.onSwipe(axis) // ignored if the tour moved on or ended meanwhile
                }
            }
        }
    }

    val dark = isSystemInDarkTheme()
    val methodColor = state.method?.let { Color(it.colorArgb) } ?: colors.bg
    val tint by animateColorAsState(
        targetValue = lerp(colors.bg, methodColor, if (dark) TINT_DARK else TINT_LIGHT),
        animationSpec = tween(250),
        label = "methodTint",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(tint)
            .onGloballyPositioned { bounds.root = it }
            .pointerInput(Unit) {
                // Children that handle their own taps (buttons, keys) consume them and never get here.
                detectTapGestures { pos -> if (!bounds.contains(pos)) focus.clearFocus() }
            },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().then(hidden).padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(state.today.format(dateFormat), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Today ${Money.format(state.todayTotal)}", fontSize = 12.sp, color = colors.muted)
                }
                IconButton(
                    onClick = { if (!touring) onOpenExpenses() },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = colors.surface2, contentColor = colors.fg),
                    modifier = Modifier.size(38.dp).guideTarget(targets, GuideTarget.HISTORY_ICON),
                ) { Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = "Expenses list") }
                Box(Modifier.padding(start = 8.dp)) {
                    var menu by remember { mutableStateOf(false) }
                    // The menu is a popup drawn above the overlay, so the tour closes it.
                    LaunchedEffect(touring) { if (touring) menu = false }
                    IconButton(
                        onClick = { if (!touring) menu = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = colors.surface2, contentColor = colors.fg),
                        modifier = Modifier.size(38.dp),
                    ) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Labels") }, onClick = { menu = false; onOpenLabels() })
                        DropdownMenuItem(text = { Text("Payment methods") }, onClick = { menu = false; onOpenMethods() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onOpenSettings() })
                    }
                }
            }

            SwipeArea(
                state = state,
                onMethod = { onSwipe(HomeSwipe.Axis.METHOD, it) },
                onLabel = { onSwipe(HomeSwipe.Axis.LABEL, it) },
                modifier = Modifier.weight(1f).fillMaxWidth().then(swipeHidden).guideTarget(targets, GuideTarget.SWIPE_AREA),
            )

            DescriptionField(
                vm = vm,
                voice = rememberVoiceInput(voiceLanguage, vm::setDescriptionFromVoice),
                locked = touring,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(hidden)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                    .onGloballyPositioned { bounds.field = it }
                    .guideTarget(targets, GuideTarget.DESCRIPTION),
            )

            Numpad(
                onKey = { key -> if (!touring) { focus.clearFocus(); vm.onKey(key) } },
                modifier = Modifier.then(hidden).guideTarget(targets, GuideTarget.NUMPAD, part = 0),
            )
            Button(
                onClick = { if (!touring) { focus.clearFocus(); vm.enter() } },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(hidden)
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 16.dp)
                    .guideTarget(targets, GuideTarget.NUMPAD, part = 1)
                    .height(58.dp),
            ) { Text("Enter", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp),
        )
        tourStep?.let { step ->
            CoachOverlay(
                step = step,
                targets = targets,
                onNext = guide::next,
                onSkip = guide::skip,
                onSwipe = { onSwipe(it.axis, it.delta) },
            )
        }
    }
}

/** Middle area: drag left/right for the payment method, up/down for the label. */
@Composable
private fun SwipeArea(
    state: HomeState,
    onMethod: (Int) -> Unit,
    onLabel: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GastosTheme.colors
    val currentOnMethod by rememberUpdatedState(onMethod)
    val currentOnLabel by rememberUpdatedState(onLabel)
    Column(
        modifier
            .pointerInput(Unit) {
                val threshold = HomeSwipeThreshold.toPx()
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragCancel = { total = Offset.Zero },
                    onDragEnd = {
                        val d = HomeSwipe.decide(total.x, total.y, threshold)
                        when (d.axis) {
                            HomeSwipe.Axis.METHOD -> currentOnMethod(d.delta)
                            HomeSwipe.Axis.LABEL -> currentOnLabel(d.delta)
                            HomeSwipe.Axis.NONE -> Unit
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    total += amount
                }
            }
            .semantics(mergeDescendants = true) {
                customActions = listOf(
                    CustomAccessibilityAction("Previous payment method") { currentOnMethod(-1); true },
                    CustomAccessibilityAction("Next payment method") { currentOnMethod(+1); true },
                    CustomAccessibilityAction("Previous label") { currentOnLabel(-1); true },
                    CustomAccessibilityAction("Next label") { currentOnLabel(+1); true },
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Hint("‹", 18)
            Text(
                (state.method?.name ?: "").uppercase(Locale.ENGLISH),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = colors.fg,
                maxLines = 1,
            )
            Hint("›", 18)
        }
        Row(
            Modifier
                .background(colors.surface.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Hint("˄", 11)
            Text(state.label?.name ?: "...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.fg, maxLines = 1)
            Hint("˅", 11)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("₱", fontSize = 30.sp, color = colors.muted, modifier = Modifier.padding(end = 4.dp, bottom = 5.dp))
            Text(
                AmountInput.display(state.input),
                fontSize = 50.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                maxLines = 1,
                color = colors.fg,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            state.methods.forEach { m ->
                val on = m.id == state.method?.id
                Box(
                    Modifier
                        .height(6.dp)
                        .width(if (on) 16.dp else 6.dp)
                        .background(colors.fg.copy(alpha = if (on) 0.8f else 0.2f), RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

private fun TextFieldValue.atEnd() = copy(selection = TextRange(text.length))

/** Faint swipe-direction glyph; decorative, so screen readers skip it. */
@Composable
private fun Hint(text: String, size: Int) {
    Text(
        text,
        fontSize = size.sp,
        color = GastosTheme.colors.fg.copy(alpha = 0.4f),
        modifier = Modifier.clearAndSetSemantics { },
    )
}

/** "What did you buy?" box with the voice mic (hidden when the phone has no recognizer). */
@Composable
private fun DescriptionField(
    vm: HomeViewModel,
    voice: (() -> Unit)?,
    /** True during the tour: the box can't be focused or edited and the mic does nothing. */
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = GastosTheme.colors
    val focus = LocalFocusManager.current
    val resets by vm.descriptionResets.collectAsStateWithLifecycle()
    // Local copy keeps typing and the cursor in sync with the keyboard. It only re-reads the VM when the
    // VM rewrites the text itself (Enter, voice, restore), so a late emission can never undo a keystroke.
    var field by remember { mutableStateOf(TextFieldValue(vm.descriptionText.value).atEnd()) }
    LaunchedEffect(resets) {
        val text = vm.descriptionText.value
        if (field.text != text) field = TextFieldValue(text).atEnd()
    }
    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it.copy(text = it.text.take(ExpenseText.DESCRIPTION_MAX))
            vm.onDescriptionChange(field.text)
        },
        placeholder = { Text("What did you buy?", color = colors.muted) },
        singleLine = true,
        readOnly = locked,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        trailingIcon = voice?.let { launch ->
            {
                IconButton(onClick = { if (!locked) { focus.clearFocus(); launch() } }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Speak description", tint = colors.muted)
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.line,
            focusedTextColor = colors.fg,
            unfocusedTextColor = colors.fg,
            cursorColor = colors.accent,
        ),
        modifier = modifier.focusProperties { canFocus = !locked },
    )
}
