package io.gastos.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class PaymentMethodRepository(
    private val db: GastosDatabase,
    private val onDataChanged: () -> Unit,
) {
    private val methods = db.paymentMethodDao()

    /** Methods shown in swipes and pickers, by position. */
    fun observeActive(): Flow<List<PaymentMethodEntity>> = methods.observeActive()

    /** Every method, archived ones included, by position. */
    fun observeAll(): Flow<List<PaymentMethodEntity>> = methods.observeAll()

    suspend fun active(): List<PaymentMethodEntity> = methods.active()

    suspend fun add(name: String, colorArgb: Int): Long {
        val id = db.withTransaction {
            methods.insert(PaymentMethodEntity(name = name, colorArgb = colorArgb, position = methods.maxPosition() + 1))
        }
        onDataChanged()
        return id
    }

    suspend fun rename(id: Long, name: String) {
        methods.rename(id, name)
        onDataChanged()
    }

    suspend fun recolor(id: Long, colorArgb: Int) {
        methods.recolor(id, colorArgb)
        onDataChanged()
    }

    suspend fun reorder(idsInOrder: List<Long>) {
        db.withTransaction { idsInOrder.forEachIndexed { index, id -> methods.setPosition(id, index) } }
        onDataChanged()
    }

    /** Hides the method; its expenses keep it. Throws [IllegalStateException] for the last active method. */
    suspend fun archive(id: Long) {
        db.withTransaction {
            val method = methods.byId(id) ?: return@withTransaction
            if (method.archived) return@withTransaction
            check(methods.activeCount() > 1) { "At least one payment method must stay active" }
            methods.archive(id)
        }
        onDataChanged()
    }
}
