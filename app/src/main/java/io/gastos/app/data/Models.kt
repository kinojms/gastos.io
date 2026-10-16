package io.gastos.app.data

import java.time.LocalDate

data class ExpenseRow(
    val id: Long,
    val amountCentavos: Long,
    val timestamp: Long,
    val labelId: Long,
    val labelName: String,
    val labelColor: Int,
    val paymentMethodId: Long,
    val paymentMethodName: String,
    val paymentMethodColor: Int,
    val description: String?,
    val notes: String?,
)

data class DaySummary(val date: LocalDate, val count: Int, val totalCentavos: Long)

data class LabelTotal(val labelId: Long, val name: String, val colorArgb: Int, val totalCentavos: Long)

data class MethodTotal(val paymentMethodId: Long, val name: String, val colorArgb: Int, val totalCentavos: Long)

/** Seeded on a fresh install and by the v1 -> v2 migration, in this order. Cash comes first. */
val DEFAULT_PAYMENT_METHODS: List<Pair<String, Int>> = listOf(
    "Cash" to 0xFF77808B.toInt(),
    "GCash" to 0xFF007DFE.toInt(),
    "Maribank" to 0xFFFF6A13.toInt(),
    "BPI" to 0xFFA6192E.toInt(),
    "Maya" to 0xFF00B26B.toInt(),
    "BDO" to 0xFF0033A0.toInt(),
)

val LABEL_PALETTE: List<Int> = listOf(
    0xFFD9822B.toInt(),
    0xFF2F7FC1.toInt(),
    0xFF8A5CC9.toInt(),
    0xFF3A9A6A.toInt(),
    0xFF77808B.toInt(),
    0xFFC2412D.toInt(),
    0xFFC9A227.toInt(),
    0xFF2A9D9D.toInt(),
)
