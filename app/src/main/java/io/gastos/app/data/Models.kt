package io.gastos.app.data

import java.time.LocalDate

data class ExpenseRow(
    val id: Long,
    val amountCentavos: Long,
    val timestamp: Long,
    val labelId: Long,
    val labelName: String,
    val labelColor: Int,
)

data class DaySummary(val date: LocalDate, val count: Int, val totalCentavos: Long)

data class LabelTotal(val labelId: Long, val name: String, val colorArgb: Int, val totalCentavos: Long)

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
