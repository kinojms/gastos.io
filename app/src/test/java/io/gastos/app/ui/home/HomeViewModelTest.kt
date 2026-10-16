package io.gastos.app.ui.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.ExpenseRepository
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.LabelRepository
import io.gastos.app.data.PaymentMethodRepository
import io.gastos.app.domain.Key
import io.gastos.app.ui.ViewModelTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private val manila = ZoneId.of("Asia/Manila")
    private val clock = Clock.system(manila)
    private lateinit var db: GastosDatabase
    private lateinit var labels: LabelRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var methods: PaymentMethodRepository
    private lateinit var prefs: AppPrefs
    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = GastosDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        labels = LabelRepository(db) { }
        expenses = ExpenseRepository(db, clock, { manila }) { }
        methods = PaymentMethodRepository(db) { }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
    }

    private val vms = ViewModelTracker()

    @After fun tearDown() {
        vms.clear() // cancel VM coroutines before resetting Main
        db.close()
        scope.cancel()
        prefsFile.delete()
        Dispatchers.resetMain()
    }

    private fun vm(dates: kotlinx.coroutines.flow.Flow<LocalDate> = MutableStateFlow(expenses.today())) =
        vms.track { HomeViewModel(expenses, labels, methods, prefs, dates) }

    private suspend fun HomeViewModel.await(cond: (HomeState) -> Boolean) =
        withTimeout(15_000) { state.first(cond) }

    private suspend fun byName(n: String) = labels.all().first { it.name == n }

    @Test fun enterSavesAndClears() = runBlocking {
        val food = byName("Food")
        val m = vm()
        m.await { it.label?.id == food.id }
        listOf(Key.Digit(4), Key.Digit(5), Key.Dot, Key.Digit(5)).forEach(m::onKey)
        m.await { it.input == "45.5" }
        m.enter()
        assertEquals("Added ₱45.50 · Food", withTimeout(15_000) { m.toasts.first() })
        val rows = expenses.rowsForDay(expenses.today())
        assertEquals(4550L, rows.single().amountCentavos)
        assertEquals(food.id, rows.single().labelId)
        assertEquals("", m.await { it.input == "" }.input)
        assertEquals("Cash", rows.single().paymentMethodName) // no last-used yet: first active
    }

    @Test fun enterSavesWithLastUsedPaymentMethod() = runBlocking {
        val maya = methods.active().first { it.name == "Maya" }
        prefs.setLastPaymentMethodId(maya.id)
        val m = vm()
        m.await { it.label != null }
        m.onKey(Key.Digit(7))
        m.await { it.input == "7" }
        m.enter()
        withTimeout(15_000) { m.toasts.first() }
        assertEquals(maya.id, expenses.rowsForDay(expenses.today()).single().paymentMethodId)
    }

    @Test fun failedSaveRestoresInputAndToasts() = runBlocking {
        // Make every insert into expenses fail, like a broken database.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_insert BEFORE INSERT ON expenses BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        val m = vm()
        m.await { it.label != null }
        listOf(Key.Digit(1), Key.Digit(2), Key.Dot, Key.Digit(5)).forEach(m::onKey)
        m.await { it.input == "12.5" }
        m.enter()
        assertEquals("Couldn't save. Try again.", withTimeout(15_000) { m.toasts.first() })
        assertEquals("12.5", m.await { it.input == "12.5" }.input)
        assertEquals(0, expenses.rowsForDay(expenses.today()).size)
    }

    @Test fun selectingLabelUsesInjectedSetter() = runBlocking {
        val transpo = byName("Transpo")
        val calls = mutableListOf<Long>()
        val m = vms.track {
            HomeViewModel(expenses, labels, methods, prefs, MutableStateFlow(expenses.today())) { id ->
                calls += id
                prefs.setLastLabelId(id)
            }
        }
        m.await { it.label != null }
        m.selectLabel(transpo.id)
        m.await { it.label?.id == transpo.id }
        assertEquals(listOf(transpo.id), calls)
    }

    @Test fun enterWithZeroDoesNothing() = runBlocking {
        val m = vm()
        m.await { it.label != null }
        m.enter()
        assertNull(withTimeoutOrNull(300) { m.toasts.firstOrNull() })
        assertEquals(0, expenses.rowsForDay(expenses.today()).size)
    }

    @Test fun selectingLabelUpdatesLastUsed() = runBlocking {
        val transpo = byName("Transpo")
        val m = vm()
        m.await { it.label != null }
        m.selectLabel(transpo.id)
        assertEquals(transpo.id, m.await { it.label?.id == transpo.id }.label?.id)
        assertEquals(transpo.id, prefs.lastLabelId.first())
    }

    @Test fun startsOnLastUsedLabel() = runBlocking {
        val bills = byName("Bills")
        prefs.setLastLabelId(bills.id)
        val m = vm()
        assertEquals(bills.id, m.await { it.label != null }.label?.id)
    }

    @Test fun rollsOverAtMidnight() = runBlocking {
        val day1 = expenses.today()
        val ticker = MutableStateFlow(day1)
        expenses.add(48_500, byName("Food").id, methods.active().first().id)
        val m = vm(ticker)
        assertEquals(48_500L, m.await { it.todayTotal == 48_500L }.todayTotal)
        ticker.value = day1.plusDays(1)
        val s = m.await { it.today == day1.plusDays(1) && it.todayTotal == 0L }
        assertEquals(day1.plusDays(1), s.today)
    }
}
