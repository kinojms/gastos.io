package io.gastos.app.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.gastos.app.data.ExpenseRepository
import io.gastos.app.data.ExpenseRow
import io.gastos.app.export.XlsxWriter.Cell
import io.gastos.app.export.XlsxWriter.Sheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Format(val ext: String, val mime: String) {
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

object ExportFiles {
    fun dayName(d: LocalDate, ext: String) = "expenses-$d.$ext"
    fun monthName(m: YearMonth, ext: String) = "expenses-$m.$ext"
}

/** A produced export: the shareable [uri] plus the file name and MIME type. */
class ExportResult(val uri: Uri, val fileName: String, val mime: String)

class ExportService(
    private val context: Context,
    private val expenses: ExpenseRepository,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val uriFor: (File) -> Uri = { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) },
) {
    suspend fun exportDay(date: LocalDate, format: Format): Uri = exportDayFile(date, format).uri

    suspend fun exportMonth(month: YearMonth, format: Format): Uri = exportMonthFile(month, format).uri

    suspend fun exportDayFile(date: LocalDate, format: Format): ExportResult {
        // rowsForDay is newest-first (UI order); exports are chronological like the month export.
        val rows = expenses.rowsForDay(date).sortedWith(compareBy({ it.timestamp }, { it.id }))
        return store(ExportFiles.dayName(date, format.ext), format, bytes(rows, format))
    }

    suspend fun exportMonthFile(month: YearMonth, format: Format): ExportResult {
        val rows = expenses.rowsForMonth(month)
        val data = if (format == Format.CSV) CsvExporter.write(rows, zone()) else monthXlsx(rows)
        return store(ExportFiles.monthName(month, format.ext), format, data)
    }

    private fun bytes(rows: List<ExpenseRow>, format: Format): ByteArray =
        if (format == Format.CSV) CsvExporter.write(rows, zone()) else XlsxWriter.write(listOf(expensesSheet(rows)))

    private fun monthXlsx(rows: List<ExpenseRow>): ByteArray = XlsxWriter.write(
        listOf(
            expensesSheet(rows),
            totalsSheet("By label", "Label", rows) { it.labelName },
            totalsSheet("By payment", "Payment", rows) { it.paymentMethodName },
        ),
    )

    /** One row per group (largest first) with its amount, then a Total row. */
    private fun totalsSheet(name: String, groupHeader: String, rows: List<ExpenseRow>, key: (ExpenseRow) -> String): Sheet {
        val totals = rows.groupBy(key)
            .map { (group, rs) -> group to rs.sumOf { it.amountCentavos } }
            .sortedByDescending { it.second }
        return Sheet(
            name = name,
            header = listOf(groupHeader, "Amount"),
            rows = totals.map { listOf(Cell.Text(it.first), Cell.Peso(it.second)) },
            totalLabelCol = 0,
            sumCols = listOf(1),
        )
    }

    private fun expensesSheet(rows: List<ExpenseRow>): Sheet {
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val z = zone()
        fun optional(s: String?): Cell = if (s.isNullOrEmpty()) Cell.Blank else Cell.Text(s)
        return Sheet(
            name = "Expenses",
            header = listOf("Date", "Time", "Label", "Payment", "Description", "Amount", "Notes"),
            rows = rows.map {
                val at = Instant.ofEpochMilli(it.timestamp).atZone(z)
                listOf(
                    Cell.Date(at.toLocalDate()),
                    Cell.Text(at.format(time)),
                    Cell.Text(it.labelName),
                    Cell.Text(it.paymentMethodName),
                    optional(it.description),
                    Cell.Peso(it.amountCentavos),
                    optional(it.notes),
                )
            },
            totalLabelCol = 4, // "Total" under Description, next to the Amount sum
            sumCols = listOf(5), // Amount = column F
            wrapCols = setOf(6), // Notes
        )
    }

    private suspend fun store(name: String, format: Format, data: ByteArray): ExportResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(data)
        ExportResult(uriFor(file), name, format.mime)
    }
}
