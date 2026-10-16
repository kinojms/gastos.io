package io.gastos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.ExpenseRepository
import io.gastos.app.data.LabelEntity
import io.gastos.app.data.LabelRepository
import io.gastos.app.data.PaymentMethodRepository
import io.gastos.app.domain.AmountInput
import io.gastos.app.domain.Key
import io.gastos.app.domain.Money
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class HomeState(
    val input: String,
    val label: LabelEntity?,
    val labels: List<LabelEntity>,
    val todayTotal: Long,
    val today: LocalDate,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val expenses: ExpenseRepository,
    labels: LabelRepository,
    private val methods: PaymentMethodRepository,
    private val prefs: AppPrefs,
    /** Emits the current date, and again whenever it changes (midnight rollover). */
    dates: Flow<LocalDate> = dateTicker(expenses),
    /** Persists the shared last-used label; the app wires this to also refresh the widget. */
    private val setLastLabel: suspend (Long) -> Unit = prefs::setLastLabelId,
) : ViewModel() {
    private val input = MutableStateFlow("")
    private val toastChannel = Channel<String>(Channel.BUFFERED)

    val toasts: Flow<String> = toastChannel.receiveAsFlow()

    private val today = dates.distinctUntilChanged()
    private val total = today.flatMapLatest { expenses.observeDayTotal(it) }

    val state: StateFlow<HomeState> = combine(
        input, labels.observeAll(), prefs.lastLabelId, today, total,
    ) { text, all, lastId, date, sum ->
        HomeState(
            input = text,
            label = all.firstOrNull { it.id == lastId } ?: all.firstOrNull(),
            labels = all,
            todayTotal = sum,
            today = date,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeState("", null, emptyList(), 0L, expenses.today()),
    )

    fun onKey(key: Key) {
        input.value = AmountInput.apply(input.value, key)
    }

    fun selectLabel(id: Long) {
        viewModelScope.launch { setLastLabel(id) }
    }

    fun enter() {
        val cents = AmountInput.toCentavos(input.value) ?: return
        val label = state.value.label ?: return
        val typed = input.value
        input.value = "" // cleared before the insert so a double tap cannot save twice
        viewModelScope.launch {
            val message = try {
                // Leaving Home must not cancel an insert the user already confirmed with Enter.
                withContext(NonCancellable) {
                    val method = prefs.resolvePaymentMethod(methods.active())
                    expenses.add(cents, label.id, method.id)
                }
                "Added ${Money.format(cents)} · ${label.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                input.compareAndSet("", typed) // give the amount back unless the user started typing again
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
