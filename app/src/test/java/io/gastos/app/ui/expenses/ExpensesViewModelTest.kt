package io.gastos.app.ui.expenses

import androidx.test.core.app.ApplicationProvider
import io.gastos.app.data.ExpenseRepository
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.LabelRepository
import io.gastos.app.ui.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExpensesViewModelTest {
    private val manila = ZoneId.of("Asia/Manila")
    private lateinit var db: GastosDatabase
    private lateinit var labels: LabelRepository
    private lateinit var expenses: ExpenseRepository

    private suspend fun cash() = db.paymentMethodDao().active().first().id

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = GastosDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        labels = LabelRepository(db) { }
        expenses = ExpenseRepository(db, Clock.system(manila), { manila }) { }
    }

    private val vms = ViewModelTracker()

    @After fun tearDown() {
        vms.clear() // cancel VM coroutines before resetting Main
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(month: YearMonth = YearMonth.of(2026, 10)) =
        vms.track { newVm(month) }

    private fun newVm(month: YearMonth = YearMonth.of(2026, 10)) =
        ExpensesViewModel(expenses, labels, MutableStateFlow(expenses.today()), { manila }, month)

    @Test fun confirmedDeleteSurvivesLeavingScreen() = runBlocking {
        val id = expenses.add(1_000, labels.all().first().id, cash())
        val store = androidx.lifecycle.ViewModelStore()
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = newVm() as T
        }
        val m = androidx.lifecycle.ViewModelProvider(store, factory)[ExpensesViewModel::class.java]
        m.delete(id)
        store.clear() // back navigation clears the ViewModel and cancels viewModelScope
        withTimeout(15_000) { while (expenses.rowsForDay(expenses.today()).isNotEmpty()) kotlinx.coroutines.delay(20) }
    }

    @Test fun monthSwitcher() = runBlocking {
        val m = vm()
        assertEquals(YearMonth.of(2026, 10), m.month.value)
        m.prevMonth()
        assertEquals(YearMonth.of(2026, 9), m.month.value)
        m.nextMonth(); m.nextMonth()
        assertEquals(YearMonth.of(2026, 11), m.month.value)
    }

    @Test fun savingEditWithNewDateMovesSummary() = runBlocking {
        val food = labels.all().first { it.name == "Food" }
        val bills = labels.all().first { it.name == "Bills" }
        val d1 = LocalDate.of(2026, 10, 5)
        val d2 = LocalDate.of(2026, 10, 9)
        val ts = d1.atTime(14, 30).atZone(manila).toInstant().toEpochMilli()
        val gcash = db.paymentMethodDao().active().first { it.name == "GCash" }.id
        val id = expenses.add(1000, food.id, gcash, "Jollibee")
        expenses.update(id, 1000, food.id, gcash, ts, "Jollibee", "with Ana")
        val m = vm()
        val before = withTimeout(15_000) { m.daySummaries.first { it.isNotEmpty() } }
        assertEquals(d1, before.single().date)

        m.saveEdit(id, 4550, bills.id, gcash, d2, LocalTime.of(9, 5), "Jollibee", "with Ana")
        val after = withTimeout(15_000) { m.daySummaries.first { s -> s.singleOrNull()?.date == d2 } }
        assertEquals(4550L, after.single().totalCentavos)
        val row = expenses.rowsForDay(d2).single()
        assertEquals(bills.id, row.labelId)
        assertEquals(gcash, row.paymentMethodId)
        assertEquals("Jollibee", row.description)
        assertEquals("with Ana", row.notes)
        assertEquals(d2.atTime(9, 5).atZone(manila).toInstant().toEpochMilli(), row.timestamp)
    }

    @Test fun deleteRemovesExpense() = runBlocking {
        val food = labels.all().first { it.name == "Food" }
        val id = expenses.add(1000, food.id, cash())
        vm().delete(id)
        assertEquals(0, expenses.rowsForDay(expenses.today()).size)
    }
}
