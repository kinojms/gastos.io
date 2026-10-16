package io.gastos.app.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ExpenseRepositoryTest {
    private val manila = ZoneId.of("Asia/Manila")
    private val clock = Clock.fixed(LocalDateTime.of(2026, 10, 16, 12, 0).atZone(manila).toInstant(), manila)
    private val oct16 = LocalDate.of(2026, 10, 16)
    private lateinit var db: GastosDatabase
    private lateinit var repo: ExpenseRepository
    private lateinit var labels: LabelRepository
    private lateinit var methods: PaymentMethodRepository
    private var changes = 0

    @Before fun setUp() {
        db = GastosDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repo = ExpenseRepository(db, clock, { manila }) { changes++ }
        labels = LabelRepository(db) { }
        methods = PaymentMethodRepository(db) { }
    }

    @After fun tearDown() = db.close()

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(manila).toInstant().toEpochMilli()

    private suspend fun id(name: String) = labels.all().first { it.name == name }.id
    private suspend fun method(name: String) = methods.observeAll().first().first { it.name == name }.id
    private suspend fun cash() = method("Cash")

    @Test fun addAppearsInToday() = runBlocking {
        repo.add(4550, id("Food"), cash())
        val rows = repo.observeDay(oct16).first()
        assertEquals(1, rows.size)
        assertEquals(4550L, rows[0].amountCentavos)
        assertEquals("Food", rows[0].labelName)
    }

    @Test fun lateNightStaysInThatDay() = runBlocking {
        val food = id("Food")
        val e = repo.add(100, food, cash())
        repo.update(e, 100, food, cash(), millis(2026, 10, 16, 23, 59), null, null)
        assertEquals(1, repo.observeDay(oct16).first().size)
        assertEquals(0, repo.observeDay(oct16.plusDays(1)).first().size)
    }

    @Test fun editingDateMovesDay() = runBlocking {
        val food = id("Food")
        val e = repo.add(4550, food, cash())
        repo.update(e, 4550, food, cash(), millis(2026, 10, 15, 9, 0), null, null)
        assertEquals(0L, repo.dayTotal(oct16))
        assertEquals(4550L, repo.dayTotal(oct16.minusDays(1)))
    }

    @Test fun daySummariesNewestFirst() = runBlocking {
        val food = id("Food")
        val a = repo.add(100, food, cash())
        repo.update(a, 100, food, cash(), millis(2026, 10, 14, 8, 0), null, null)
        val b = repo.add(200, food, cash())
        repo.update(b, 200, food, cash(), millis(2026, 10, 14, 9, 0), null, null)
        repo.add(500, food, cash())
        val s = repo.observeDaySummaries(YearMonth.of(2026, 10)).first()
        assertEquals(listOf(oct16, LocalDate.of(2026, 10, 14)), s.map { it.date })
        assertEquals(listOf(1, 2), s.map { it.count })
        assertEquals(listOf(500L, 300L), s.map { it.totalCentavos })
    }

    @Test fun monthByLabelLargestFirst() = runBlocking {
        repo.add(300, id("Food"), cash())
        repo.add(500, id("Transpo"), cash())
        val t = repo.observeMonthByLabel(YearMonth.of(2026, 10)).first()
        assertEquals(listOf("Transpo", "Food"), t.map { it.name })
    }

    @Test fun onDataChangedCalledOnEveryWrite() = runBlocking {
        val food = id("Food")
        val e = repo.add(100, food, cash())
        repo.update(e, 200, food, cash(), clock.millis(), null, null)
        repo.delete(e)
        assertEquals(3, changes)
    }

    @Test fun addStoresMethodAndCleanDescription() = runBlocking {
        val gcash = method("GCash")
        repo.add(18_500, id("Food"), gcash, "  Jollibee  ")
        repo.add(100, id("Food"), cash(), "   ")
        val rows = repo.observeDay(oct16).first().sortedBy { it.amountCentavos }
        assertEquals(listOf(null, "Jollibee"), rows.map { it.description })
        assertEquals(listOf("Cash", "GCash"), rows.map { it.paymentMethodName })
        assertEquals(gcash, rows[1].paymentMethodId)
        assertEquals(0xFF007DFE.toInt(), rows[1].paymentMethodColor)
        assertEquals(listOf<String?>(null, null), rows.map { it.notes })
        repo.add(1, id("Food"), cash(), "d".repeat(70))
        assertEquals(60, repo.rowsForDay(oct16).first { it.amountCentavos == 1L }.description!!.length)
    }

    @Test fun updateStoresNotes() = runBlocking {
        val food = id("Food")
        val e = repo.add(4550, food, cash())
        repo.update(e, 5000, id("Bills"), method("BPI"), millis(2026, 10, 16, 8, 0), " Meralco ", "  paid early\n")
        val row = repo.rowsForDay(oct16).single()
        assertEquals(5000L, row.amountCentavos)
        assertEquals("Bills", row.labelName)
        assertEquals("BPI", row.paymentMethodName)
        assertEquals("Meralco", row.description)
        assertEquals("paid early", row.notes)
        repo.update(e, 5000, id("Bills"), method("BPI"), millis(2026, 10, 16, 8, 0), "  ", "n".repeat(1200))
        val cleared = repo.rowsForDay(oct16).single()
        assertNull(cleared.description)
        assertEquals(1000, cleared.notes!!.length)
    }

    @Test fun monthByMethodLargestFirstIncludesArchived() = runBlocking {
        val food = id("Food")
        repo.add(300, food, cash())
        repo.add(200, food, cash())
        val gcash = method("GCash")
        repo.add(800, food, gcash)
        repo.add(100, food, method("BDO"))
        methods.archive(gcash)
        val t = repo.observeMonthByMethod(YearMonth.of(2026, 10)).first()
        assertEquals(listOf("GCash", "Cash", "BDO"), t.map { it.name })
        assertEquals(listOf(800L, 500L, 100L), t.map { it.totalCentavos })
        assertEquals(gcash, t[0].paymentMethodId)
        assertEquals(0xFF007DFE.toInt(), t[0].colorArgb)
        assertEquals(emptyList<MethodTotal>(), repo.observeMonthByMethod(YearMonth.of(2026, 9)).first())
    }
}
