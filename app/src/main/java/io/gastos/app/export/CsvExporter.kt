package io.gastos.app.export

import io.gastos.app.data.ExpenseRow
import io.gastos.app.domain.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** UTF-8 with BOM. Header, one line per row, then a Total line. Lines are joined with "\n". */
    fun write(rows: List<ExpenseRow>, zone: ZoneId): ByteArray {
        val lines = ArrayList<String>(rows.size + 2)
        lines += "Date,Time,Label,Payment,Description,Amount,Notes"
        for (r in rows) {
            val at = Instant.ofEpochMilli(r.timestamp).atZone(zone)
            lines += listOf(
                at.toLocalDate().toString(),
                at.format(timeFormat),
                r.labelName,
                r.paymentMethodName,
                r.description.orEmpty(),
                Money.plain(r.amountCentavos),
                r.notes.orEmpty(),
            ).joinToString(",") { quote(it) }
        }
        lines += ",,,,Total,${Money.plain(rows.sumOf { it.amountCentavos })},"
        return BOM + lines.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    private fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
