package io.gastos.app.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    private var changes = 0

    @Before fun setUp() {
        db = GastosDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repo = ExpenseRepository(db, clock, { manila }) { changes++ }
        labels = LabelRepository(db) { }
    }

    @After fun tearDown() = db.close()

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(manila).toInstant().toEpochMilli()

    private suspend fun id(name: String) = labels.all().first { it.name == name }.id

    @Test fun addAppearsInToday() = runBlocking {
        repo.add(4550, id("Food"))
        val rows = repo.observeDay(oct16).first()
        assertEquals(1, rows.size)
        assertEquals(4550L, rows[0].amountCentavos)
        assertEquals("Food", rows[0].labelName)
    }

    @Test fun lateNightStaysInThatDay() = runBlocking {
        val food = id("Food")
        val e = repo.add(100, food)
        repo.update(e, 100, food, millis(2026, 10, 16, 23, 59))
        assertEquals(1, repo.observeDay(oct16).first().size)
        assertEquals(0, repo.observeDay(oct16.plusDays(1)).first().size)
    }

    @Test fun editingDateMovesDay() = runBlocking {
        val food = id("Food")
        val e = repo.add(4550, food)
        repo.update(e, 4550, food, millis(2026, 10, 15, 9, 0))
        assertEquals(0L, repo.dayTotal(oct16))
        assertEquals(4550L, repo.dayTotal(oct16.minusDays(1)))
    }

    @Test fun daySummariesNewestFirst() = runBlocking {
        val food = id("Food")
        val a = repo.add(100, food)
        repo.update(a, 100, food, millis(2026, 10, 14, 8, 0))
        val b = repo.add(200, food)
        repo.update(b, 200, food, millis(2026, 10, 14, 9, 0))
        repo.add(500, food)
        val s = repo.observeDaySummaries(YearMonth.of(2026, 10)).first()
        assertEquals(listOf(oct16, LocalDate.of(2026, 10, 14)), s.map { it.date })
        assertEquals(listOf(1, 2), s.map { it.count })
        assertEquals(listOf(500L, 300L), s.map { it.totalCentavos })
    }

    @Test fun monthByLabelLargestFirst() = runBlocking {
        repo.add(300, id("Food"))
        repo.add(500, id("Transpo"))
        val t = repo.observeMonthByLabel(YearMonth.of(2026, 10)).first()
        assertEquals(listOf("Transpo", "Food"), t.map { it.name })
    }

    @Test fun onDataChangedCalledOnEveryWrite() = runBlocking {
        val food = id("Food")
        val e = repo.add(100, food)
        repo.update(e, 200, food, clock.millis())
        repo.delete(e)
        assertEquals(3, changes)
    }
}
