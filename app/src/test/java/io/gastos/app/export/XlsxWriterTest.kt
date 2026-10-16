package io.gastos.app.export

import io.gastos.app.export.XlsxWriter.Cell
import io.gastos.app.export.XlsxWriter.Sheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipInputStream

class XlsxWriterTest {
    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out[e.name] = String(z.readBytes(), Charsets.UTF_8)
            }
        }
        return out
    }

    private fun expensesSheet(label: String = "Food", rows: Int = 2) = Sheet(
        name = "Expenses",
        header = listOf("Date", "Time", "Label", "Amount"),
        rows = List(rows) {
            listOf(Cell.Date(LocalDate.of(2026, 10, 16)), Cell.Text("08:10"), Cell.Text(label), Cell.Peso(4550))
        },
        totalLabelCol = 2,
        sumCols = listOf(3),
        colWidths = listOf(12.0, 8.0, 22.0, 14.0),
    )

    @Test fun xlsxIsValidZipWithParts() {
        val parts = unzip(XlsxWriter.write(listOf(expensesSheet())))
        for (p in listOf(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
            "xl/styles.xml", "xl/worksheets/sheet1.xml",
        )) assertTrue("missing $p", p in parts)
    }

    @Test fun xlsxEscapesXml() {
        val parts = unzip(XlsxWriter.write(listOf(expensesSheet("A&B <x>"))))
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("A&amp;B &lt;x&gt;"))
    }

    @Test fun xlsxPesoFormatAndSum() {
        val parts = unzip(XlsxWriter.write(listOf(expensesSheet())))
        assertTrue(parts.getValue("xl/styles.xml").contains("formatCode=\"&quot;₱&quot;#,##0.00\""))
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<f>SUM(D2:D3)</f><v>91.00</v>"))
        assertTrue(sheet.contains("<pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/>"))
    }

    @Test fun xlsxDateSerial() {
        val sheet = unzip(XlsxWriter.write(listOf(expensesSheet()))).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<v>46311</v>"))
    }

    @Test fun xlsxMonthlyHasTwoSheets() {
        val by = Sheet("By label", listOf("Label", "Amount"), listOf(listOf(Cell.Text("Food"), Cell.Peso(100))), 0, listOf(1), listOf(22.0, 14.0))
        val parts = unzip(XlsxWriter.write(listOf(expensesSheet(), by)))
        val wb = parts.getValue("xl/workbook.xml")
        assertTrue(wb.contains("name=\"Expenses\"") && wb.contains("name=\"By label\""))
        assertTrue(parts.getValue("xl/worksheets/sheet2.xml").contains("<f>SUM(B2:B2)</f>"))
    }

    @Test fun xlsxEmptySheetHasPlainZeroTotal() {
        val sheet = unzip(XlsxWriter.write(listOf(expensesSheet(rows = 0)))).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<v>0.00</v>"))
        assertTrue(!sheet.contains("<f>"))
    }

    @Test fun fileNames() {
        assertEquals("expenses-2026-10-16.csv", ExportFiles.dayName(LocalDate.of(2026, 10, 16), "csv"))
        assertEquals("expenses-2026-10.xlsx", ExportFiles.monthName(YearMonth.of(2026, 10), "xlsx"))
    }
}
