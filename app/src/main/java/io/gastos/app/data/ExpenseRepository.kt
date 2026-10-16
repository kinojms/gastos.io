package io.gastos.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ExpenseRepository(
    db: GastosDatabase,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val onDataChanged: () -> Unit,
) {
    private val dao = db.expenseDao()

    fun today(): LocalDate = LocalDate.now(clock.withZone(zone()))

    suspend fun add(amountCentavos: Long, labelId: Long): Long {
        val id = dao.insert(ExpenseEntity(amountCentavos = amountCentavos, labelId = labelId, timestamp = clock.millis()))
        onDataChanged()
        return id
    }

    suspend fun update(id: Long, amountCentavos: Long, labelId: Long, timestamp: Long) {
        dao.update(id, amountCentavos, labelId, timestamp)
        onDataChanged()
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        onDataChanged()
    }

    fun observeDay(date: LocalDate): Flow<List<ExpenseRow>> =
        dayRange(date).let { (s, e) -> dao.observeRows(s, e) }

    fun observeDayTotal(date: LocalDate): Flow<Long> =
        dayRange(date).let { (s, e) -> dao.observeTotal(s, e) }

    suspend fun dayTotal(date: LocalDate): Long = dayRange(date).let { (s, e) -> dao.total(s, e) }

    fun observeDaySummaries(month: YearMonth): Flow<List<DaySummary>> {
        val z = zone()
        val (s, e) = monthRange(month, z)
        return dao.observeEntities(s, e).map { list ->
            list.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(z).toLocalDate() }
                .map { (date, items) -> DaySummary(date, items.size, items.sumOf { it.amountCentavos }) }
                .sortedByDescending { it.date }
        }
    }

    fun observeMonthByLabel(month: YearMonth): Flow<List<LabelTotal>> =
        monthRange(month, zone()).let { (s, e) -> dao.observeByLabel(s, e) }

    suspend fun rowsForDay(date: LocalDate): List<ExpenseRow> = dayRange(date).let { (s, e) -> dao.rows(s, e) }

    suspend fun rowsForMonth(month: YearMonth): List<ExpenseRow> =
        monthRange(month, zone()).let { (s, e) -> dao.rowsOldestFirst(s, e) }

    private fun dayRange(date: LocalDate): Pair<Long, Long> {
        val z = zone()
        return date.atStartOfDay(z).toInstant().toEpochMilli() to
            date.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
    }

    private fun monthRange(month: YearMonth, z: ZoneId): Pair<Long, Long> =
        month.atDay(1).atStartOfDay(z).toInstant().toEpochMilli() to
            month.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
}
