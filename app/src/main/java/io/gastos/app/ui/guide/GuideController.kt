package io.gastos.app.ui.guide

import io.gastos.app.data.AppPrefs
import io.gastos.app.ui.home.HomeSwipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State machine for the Home coach-mark tour. [startAuto] marks the tour seen when it ends;
 * [start] (replay from Settings) never touches the stored flag.
 */
class GuideController(private val prefs: AppPrefs, private val scope: CoroutineScope) {
    private val _step = MutableStateFlow<GuideStep?>(null)
    val step: StateFlow<GuideStep?> = _step.asStateFlow()

    private var auto = false

    /** Replay: starts at the first step; finishing does not write the seen flag. */
    fun start() {
        auto = false
        _step.value = GuideStep.AMOUNT
    }

    /** Automatic first-run tour: finishing (Skip, Done or app stop) writes [GUIDE_VERSION]. */
    fun startAuto() {
        auto = true
        _step.value = GuideStep.AMOUNT
    }

    suspend fun startIfUnseen() {
        if (_step.value != null) return
        if (prefs.guideSeenVersion.first() < GUIDE_VERSION && _step.value == null) startAuto()
    }

    /** Advances past a non-swipe step; on the last step it finishes. Ignored on swipe steps. */
    fun next() {
        val current = _step.value ?: return
        if (current.swipeAxis != null) return
        if (current.isLast) finish() else _step.value = GuideStep.values()[current.ordinal + 1]
    }

    fun onSwipe(axis: HomeSwipe.Axis) {
        val current = _step.value ?: return
        if (current.swipeAxis == null || current.swipeAxis != axis) return
        _step.value = GuideStep.values()[current.ordinal + 1]
    }

    fun skip() = finish()

    fun finish() {
        if (_step.value == null) return
        _step.value = null
        if (auto) {
            auto = false
            scope.launch { withContext(NonCancellable) { prefs.setGuideSeenVersion(GUIDE_VERSION) } }
        }
    }

    fun onAppStopped() {
        if (_step.value != null) finish()
    }
}
