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

/** Archived methods are hidden from pickers and swipes but keep their name in history. */
@Entity("payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val position: Int,
    val archived: Boolean = false,
)

@Entity("expenses", indices = [Index("timestamp"), Index("labelId"), Index("paymentMethodId")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCentavos: Long,
    val labelId: Long,
    val timestamp: Long, // epoch millis
    val paymentMethodId: Long,
    val description: String? = null,
    val notes: String? = null,
)
