package io.gastos.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods WHERE archived = 0 ORDER BY position ASC, id ASC")
    fun observeActive(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE archived = 0 ORDER BY position ASC, id ASC")
    suspend fun active(): List<PaymentMethodEntity>

    @Query("SELECT * FROM payment_methods ORDER BY position ASC, id ASC")
    suspend fun all(): List<PaymentMethodEntity>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun byId(id: Long): PaymentMethodEntity?

    @Query("SELECT COUNT(*) FROM payment_methods WHERE archived = 0")
    suspend fun activeCount(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM payment_methods")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(method: PaymentMethodEntity): Long

    @Query("UPDATE payment_methods SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE payment_methods SET colorArgb = :color WHERE id = :id")
    suspend fun recolor(id: Long, color: Int)

    @Query("UPDATE payment_methods SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("UPDATE payment_methods SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}
