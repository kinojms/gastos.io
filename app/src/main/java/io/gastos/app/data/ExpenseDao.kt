package io.gastos.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("UPDATE expenses SET amountCentavos = :amount, labelId = :labelId, timestamp = :timestamp WHERE id = :id")
    suspend fun update(id: Long, amount: Long, labelId: Long, timestamp: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE expenses SET labelId = :toId WHERE labelId = :fromId")
    suspend fun moveLabel(fromId: Long, toId: Long)

    @Query("SELECT COUNT(*) FROM expenses WHERE labelId = :labelId")
    suspend fun countByLabel(labelId: Long): Int

    @Query(
        "SELECT e.id AS id, e.amountCentavos AS amountCentavos, e.timestamp AS timestamp, " +
            "e.labelId AS labelId, l.name AS labelName, l.colorArgb AS labelColor " +
            "FROM expenses e JOIN labels l ON l.id = e.labelId " +
            "WHERE e.timestamp >= :start AND e.timestamp < :end ORDER BY e.timestamp DESC, e.id DESC"
    )
    fun observeRows(start: Long, end: Long): Flow<List<ExpenseRow>>

    @Query(
        "SELECT e.id AS id, e.amountCentavos AS amountCentavos, e.timestamp AS timestamp, " +
            "e.labelId AS labelId, l.name AS labelName, l.colorArgb AS labelColor " +
            "FROM expenses e JOIN labels l ON l.id = e.labelId " +
            "WHERE e.timestamp >= :start AND e.timestamp < :end ORDER BY e.timestamp DESC, e.id DESC"
    )
    suspend fun rows(start: Long, end: Long): List<ExpenseRow>

    @Query(
        "SELECT e.id AS id, e.amountCentavos AS amountCentavos, e.timestamp AS timestamp, " +
            "e.labelId AS labelId, l.name AS labelName, l.colorArgb AS labelColor " +
            "FROM expenses e JOIN labels l ON l.id = e.labelId " +
            "WHERE e.timestamp >= :start AND e.timestamp < :end ORDER BY e.timestamp ASC, e.id ASC"
    )
    suspend fun rowsOldestFirst(start: Long, end: Long): List<ExpenseRow>

    @Query("SELECT COALESCE(SUM(amountCentavos), 0) FROM expenses WHERE timestamp >= :start AND timestamp < :end")
    fun observeTotal(start: Long, end: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountCentavos), 0) FROM expenses WHERE timestamp >= :start AND timestamp < :end")
    suspend fun total(start: Long, end: Long): Long

    @Query("SELECT * FROM expenses WHERE timestamp >= :start AND timestamp < :end")
    fun observeEntities(start: Long, end: Long): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT l.id AS labelId, l.name AS name, l.colorArgb AS colorArgb, SUM(e.amountCentavos) AS totalCentavos " +
            "FROM expenses e JOIN labels l ON l.id = e.labelId " +
            "WHERE e.timestamp >= :start AND e.timestamp < :end " +
            "GROUP BY l.id ORDER BY totalCentavos DESC, l.position ASC"
    )
    fun observeByLabel(start: Long, end: Long): Flow<List<LabelTotal>>
}
