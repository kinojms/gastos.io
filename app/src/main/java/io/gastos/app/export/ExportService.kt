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
) {
    suspend fun exportDay(date: LocalDate, format: Format): Uri = exportDayFile(date, format).uri

    suspend fun exportMonth(month: YearMonth, format: Format): Uri = exportMonthFile(month, format).uri

    suspend fun exportDayFile(date: LocalDate, format: Format): ExportResult {
        val rows = expenses.rowsForDay(date)
        return store(ExportFiles.dayName(date, format.ext), format, bytes(rows, format))
    }

    suspend fun exportMonthFile(month: YearMonth, format: Format): ExportResult {
        val rows = expenses.rowsForMonth(month)
        val data = if (format == Format.CSV) CsvExporter.write(rows, zone()) else monthXlsx(rows)
        return store(ExportFiles.monthName(month, format.ext), format, data)
    }

    private fun bytes(rows: List<ExpenseRow>, format: Format): ByteArray =
        if (format == Format.CSV) CsvExporter.write(rows, zone()) else XlsxWriter.write(listOf(expensesSheet(rows)))

    private fun monthXlsx(rows: List<ExpenseRow>): ByteArray {
        val byLabel = rows.groupBy { it.labelName }
            .map { (name, rs) -> name to rs.sumOf { it.amountCentavos } }
            .sortedByDescending { it.second }
        val labelSheet = Sheet(
            name = "By label",
            header = listOf("Label", "Amount"),
            rows = byLabel.map { listOf(Cell.Text(it.first), Cell.Peso(it.second)) },
            totalLabelCol = 0,
            sumCols = listOf(1),
            colWidths = listOf(22.0, 14.0),
        )
        return XlsxWriter.write(listOf(expensesSheet(rows), labelSheet))
    }

    private fun expensesSheet(rows: List<ExpenseRow>): Sheet {
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val z = zone()
        return Sheet(
            name = "Expenses",
            header = listOf("Date", "Time", "Label", "Amount"),
            rows = rows.map {
                val at = Instant.ofEpochMilli(it.timestamp).atZone(z)
                listOf(Cell.Date(at.toLocalDate()), Cell.Text(at.format(time)), Cell.Text(it.labelName), Cell.Peso(it.amountCentavos))
            },
            totalLabelCol = 2,
            sumCols = listOf(3),
            colWidths = listOf(12.0, 8.0, 22.0, 14.0),
        )
    }

    private suspend fun store(name: String, format: Format, data: ByteArray): ExportResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(data)
        ExportResult(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), name, format.mime)
    }
}
