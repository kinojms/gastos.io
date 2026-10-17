package io.gastos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.gastos.app.backup.BackupReminder
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.ExpenseRepository
import io.gastos.app.data.LabelEntity
import io.gastos.app.data.LabelRepository
import io.gastos.app.data.LastUsed
import io.gastos.app.data.PaymentMethodEntity
import io.gastos.app.data.PaymentMethodRepository
import io.gastos.app.domain.AmountInput
import io.gastos.app.domain.ExpenseText
import io.gastos.app.domain.Key
import io.gastos.app.domain.Money
import io.gastos.app.ui.components.VoiceLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class HomeState(
    val input: String,
    val label: LabelEntity?,
    val labels: List<LabelEntity>,
    val todayTotal: Long,
    val today: LocalDate,
    val method: PaymentMethodEntity? = null,
    val methods: List<PaymentMethodEntity> = emptyList(),
    val description: String = "",
)

private data class Selection(
    val label: LabelEntity?,
    val labels: List<LabelEntity>,
    val method: PaymentMethodEntity?,
    val methods: List<PaymentMethodEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val expenses: ExpenseRepository,
    private val labels: LabelRepository,
    private val methods: PaymentMethodRepository,
    private val prefs: AppPrefs,
    /** Emits the current date, and again whenever it changes (midnight rollover). */
    dates: Flow<LocalDate> = dateTicker(expenses),
    /** Whether a GitHub repo and token are saved; the backup reminder hides while they are. */
    hasGitHub: Flow<Boolean> = flowOf(false),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /** True while the guided tour runs; typing and Enter are ignored then so the tour never saves. */
    private val tourActive: () -> Boolean = { false },
    /** Persists the shared last-used payment method; the app wires this to also refresh the widget. */
    private val setLastMethod: suspend (Long) -> Unit = prefs::setLastPaymentMethodId,
    /** Persists the shared last-used label; the app wires this to also refresh the widget. */
    private val setLastLabel: suspend (Long) -> Unit = prefs::setLastLabelId,
) : ViewModel() {
    private val input = MutableStateFlow("")
    private val description = MutableStateFlow("")
    private val toastChannel = Channel<String>(Channel.BUFFERED)

    /** Serializes swipes so each one steps from the previous one's saved result. */
    private val swipeLock = Mutex()

    val toasts: Flow<String> = toastChannel.receiveAsFlow()

    /** The description box text, updated synchronously so the text field never lags the keyboard. */
    val descriptionText: StateFlow<String> = description.asStateFlow()

    private val descriptionRevision = MutableStateFlow(0)

    /** Bumps whenever the VM itself rewrites the description (Enter, voice, failed-save restore), never on typing. */
    val descriptionResets: StateFlow<Int> = descriptionRevision.asStateFlow()

    val voiceLanguage: StateFlow<VoiceLanguage> =
        prefs.voiceLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceLanguage.ENGLISH)

    /** Re-evaluated whenever the date ticker emits, so the reminder can appear without a restart. */
    val showBackupReminder: StateFlow<Boolean> = combine(
        hasGitHub, prefs.lastFileBackupAt, expenses.observeOldestTimestamp(), prefs.backupReminderDismissedAt, dates,
    ) { gh, lastFile, oldest, dismissed, _ ->
        BackupReminder.shouldShow(nowMillis(), gh, lastFile, oldest, dismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissBackupReminder() {
        viewModelScope.launch { prefs.setBackupReminderDismissedAt(nowMillis()) }
    }

    private val today = dates.distinctUntilChanged()
    private val total = today.flatMapLatest { expenses.observeDayTotal(it) }

    private val selection = combine(
        labels.observeAll(), prefs.lastLabelId, methods.observeActive(), prefs.lastPaymentMethodId,
    ) { allLabels, lastLabelId, active, lastMethodId ->
        Selection(
            label = allLabels.firstOrNull { it.id == lastLabelId } ?: allLabels.firstOrNull(),
            labels = allLabels,
            method = LastUsed.method(active, lastMethodId),
            methods = active,
        )
    }

    val state: StateFlow<HomeState> = combine(
        input, description, selection, today, total,
    ) { text, desc, sel, date, sum ->
        HomeState(
            input = text,
            label = sel.label,
            labels = sel.labels,
            todayTotal = sum,
            today = date,
            method = sel.method,
            methods = sel.methods,
            description = desc,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeState("", null, emptyList(), 0L, expenses.today()),
    )

    fun onKey(key: Key) {
        if (tourActive()) return
        input.value = AmountInput.apply(input.value, key)
    }

    fun onDescriptionChange(text: String) {
        description.value = text.take(ExpenseText.DESCRIPTION_MAX)
    }

    /** Voice result replaces the box text (trimmed, cut to the max length). */
    fun setDescriptionFromVoice(text: String) {
        ExpenseText.description(text)?.let {
            description.value = it
            descriptionRevision.value++
        }
    }

    fun selectLabel(id: Long) {
        viewModelScope.launch { setLastLabel(id) }
    }

    /** Steps the payment method by [delta] (+1 next, -1 previous), wrapping, and saves it as last-used. */
    fun swipeMethod(delta: Int) {
        viewModelScope.launch {
            swipeLock.withLock {
                val active = methods.active()
                val current = LastUsed.method(active, prefs.lastPaymentMethodId.first()) ?: return@withLock
                val next = active[HomeSwipe.step(active, active.indexOf(current), delta)]
                if (next.id != current.id) setLastMethod(next.id)
            }
        }
    }

    /** Steps the label by [delta] (+1 next, -1 previous), wrapping, and saves it as last-used. */
    fun swipeLabel(delta: Int) {
        viewModelScope.launch {
            swipeLock.withLock {
                val all = labels.all()
                if (all.isEmpty()) return@withLock
                val current = prefs.resolveLabel(all)
                val next = all[HomeSwipe.step(all, all.indexOf(current), delta)]
                if (next.id != current.id) setLastLabel(next.id)
            }
        }
    }

    fun enter() {
        if (tourActive()) return
        val cents = AmountInput.toCentavos(input.value) ?: return
        val shown = state.value
        val label = shown.label ?: return
        val typed = input.value
        val typedDescription = description.value
        // Cleared before the insert so a double tap cannot save twice.
        input.value = ""
        description.value = ""
        descriptionRevision.value++
        viewModelScope.launch {
            val message = try {
                // Leaving Home must not cancel an insert the user already confirmed with Enter.
                withContext(NonCancellable) {
                    val method = shown.method ?: prefs.resolvePaymentMethod(methods.active())
                    val desc = ExpenseText.description(typedDescription)
                    expenses.add(cents, label.id, method.id, desc)
                    "Added ${Money.format(cents)} · ${desc ?: label.name} · ${method.name}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Give the text back unless the user started typing again.
                input.compareAndSet("", typed)
                if (description.compareAndSet("", typedDescription)) descriptionRevision.value++
                "Couldn't save. Try again."
            }
            toastChannel.send(message)
        }
    }

    companion object {
        fun dateTicker(expenses: ExpenseRepository, periodMs: Long = 15_000): Flow<LocalDate> = flow {
            while (true) {
                emit(expenses.today())
                delay(periodMs)
            }
        }
    }
}
