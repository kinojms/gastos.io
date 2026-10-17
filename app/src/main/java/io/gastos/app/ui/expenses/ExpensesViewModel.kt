package io.gastos.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.DaySummary
import io.gastos.app.data.ExpenseRepository
import io.gastos.app.data.ExpenseRow
import io.gastos.app.data.LabelEntity
import io.gastos.app.data.LabelRepository
import io.gastos.app.data.LabelTotal
import io.gastos.app.data.MethodTotal
import io.gastos.app.data.PaymentMethodEntity
import io.gastos.app.data.PaymentMethodRepository
import io.gastos.app.export.ExportResult
import io.gastos.app.export.ExportService
import io.gastos.app.export.Format
import io.gastos.app.ui.components.VoiceLanguage
import io.gastos.app.ui.home.HomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
    private val expenses: ExpenseRepository,
    labelRepo: LabelRepository,
    methodRepo: PaymentMethodRepository,
    private val prefs: AppPrefs,
    /** Emits the current date, and again whenever it changes (midnight rollover). */
    dates: Flow<LocalDate> = HomeViewModel.dateTicker(expenses),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    initialMonth: YearMonth = YearMonth.from(expenses.today()),
    private val exporter: ExportService? = null,
) : ViewModel() {
    private val monthState = MutableStateFlow(initialMonth)
    val month: StateFlow<YearMonth> = monthState

    /** Null until loaded, so the list never peeks before it knows whether the hint was already shown. */
    val swipeHintSeen: StateFlow<Boolean?> = prefs.swipeHintSeen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markSwipeHintSeen(): Job = viewModelScope.launch { withContext(NonCancellable) { prefs.setSwipeHintSeen() } }

    val today: StateFlow<LocalDate> = dates.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), expenses.today())

    val labels: StateFlow<List<LabelEntity>> = labelRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val daySummaries: StateFlow<List<DaySummary>> = monthState
        .flatMapLatest { expenses.observeDaySummaries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthByLabel: StateFlow<List<LabelTotal>> = monthState
        .flatMapLatest { expenses.observeMonthByLabel(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val methodTotals: StateFlow<List<MethodTotal>> = monthState
        .flatMapLatest { expenses.observeMonthByMethod(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val voiceLanguage: StateFlow<VoiceLanguage> = prefs.voiceLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceLanguage.ENGLISH)

    private val allMethods = methodRepo.observeAll()

    /** Active methods plus [currentId] when that one is archived, by position. */
    fun pickerMethods(currentId: Long): Flow<List<PaymentMethodEntity>> =
        allMethods.map { list -> list.filter { !it.archived || it.id == currentId } }

    fun prevMonth() { monthState.value = monthState.value.minusMonths(1) }
    fun nextMonth() { monthState.value = monthState.value.plusMonths(1) }

    fun rowsFor(date: LocalDate): Flow<List<ExpenseRow>> = expenses.observeDay(date)
    fun totalFor(date: LocalDate): Flow<Long> = expenses.observeDayTotal(date)

    /** Timestamp for [date] at [time] in the device zone. */
    fun timestampOf(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone()).toInstant().toEpochMilli()

    fun saveEdit(
        id: Long,
        amountCentavos: Long,
        labelId: Long,
        paymentMethodId: Long,
        date: LocalDate,
        time: LocalTime,
        description: String?,
        notes: String?,
    ) {
        // NonCancellable: leaving the screen right after confirming must not drop the write.
        viewModelScope.launch {
            withContext(NonCancellable) {
                expenses.update(id, amountCentavos, labelId, paymentMethodId, timestampOf(date, time), description, notes)
            }
        }
    }

    suspend fun exportDay(date: LocalDate, format: Format): ExportResult =
        requireNotNull(exporter) { "Export unavailable" }.exportDayFile(date, format)

    suspend fun exportMonth(month: YearMonth, format: Format): ExportResult =
        requireNotNull(exporter) { "Export unavailable" }.exportMonthFile(month, format)

    fun delete(id: Long) {
        viewModelScope.launch { withContext(NonCancellable) { expenses.delete(id) } }
    }
}
