package io.gastos.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class LabelRepository(
    private val db: GastosDatabase,
    private val onDataChanged: () -> Unit,
) {
    private val labels = db.labelDao()
    private val expenses = db.expenseDao()

    fun observeAll(): Flow<List<LabelEntity>> = labels.observeAll()

    suspend fun all(): List<LabelEntity> = labels.all()

    suspend fun add(name: String, colorArgb: Int): Long {
        val id = db.withTransaction {
            labels.insert(LabelEntity(name = name, colorArgb = colorArgb, position = labels.maxPosition() + 1))
        }
        onDataChanged()
        return id
    }

    suspend fun rename(id: Long, name: String) {
        labels.rename(id, name)
        onDataChanged()
    }

    suspend fun recolor(id: Long, colorArgb: Int) {
        labels.recolor(id, colorArgb)
        onDataChanged()
    }

    suspend fun reorder(idsInOrder: List<Long>) {
        db.withTransaction { idsInOrder.forEachIndexed { index, id -> labels.setPosition(id, index) } }
        onDataChanged()
    }

    suspend fun delete(id: Long) {
        db.withTransaction {
            val label = labels.byId(id) ?: return@withTransaction
            require(!label.isOther) { "The Other label cannot be deleted" }
            val other = requireNotNull(labels.other()) { "Other label missing" }
            expenses.moveLabel(id, other.id)
            labels.delete(id)
        }
        onDataChanged()
    }

    suspend fun expenseCount(id: Long): Int = expenses.countByLabel(id)
}
