package io.gastos.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity("labels")
data class LabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val position: Int,
    val isOther: Boolean = false,
)

@Entity("expenses", indices = [Index("timestamp"), Index("labelId")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCentavos: Long,
    val labelId: Long,
    val timestamp: Long, // epoch millis
)
