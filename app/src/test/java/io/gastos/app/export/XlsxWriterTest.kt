package io.gastos.app.export

import io.gastos.app.export.XlsxWriter.Cell
import io.gastos.app.export.XlsxWriter.Sheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
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
        val by = Sheet("By label", listOf("Label", "Amount"), listOf(listOf(Cell.Text("Food"), Cell.Peso(100))), 0, listOf(1))
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

    @Test fun columnsAreAutoSizedToContent() {
        val s = Sheet(
            name = "Expenses",
            header = listOf("Date", "Time", "Label", "Amount"),
            rows = listOf(
                listOf(Cell.Date(LocalDate.of(2026, 10, 16)), Cell.Text("08:10"), Cell.Text("Groceries and more"), Cell.Peso(123_456_78)),
                listOf(Cell.Date(LocalDate.of(2026, 10, 16)), Cell.Text("09:00"), Cell.Text("Food"), Cell.Peso(4550)),
            ),
            totalLabelCol = 2,
            sumCols = listOf(3),
        )
        // Longest text + 2: dates count as 10, "08:10" (5) beats "Time" (4), "Groceries and more" (18),
        // and the total "₱123,502.28" (11) beats every row amount.
        assertEquals(listOf(12.0, 7.0, 20.0, 13.0), XlsxWriter.autoWidths(s))
        val sheet = unzip(XlsxWriter.write(listOf(s))).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("""<col min="3" max="3" width="20.0" customWidth="1"/>"""))
        assertTrue(sheet.contains("""<col min="4" max="4" width="13.0" customWidth="1"/>"""))
    }

    @Test fun headerWidensNarrowColumns() {
        val s = Sheet("By label", listOf("Label", "Amount"), listOf(listOf(Cell.Text("Gym"), Cell.Peso(100))), 0, listOf(1))
        // "Label" (5) > "Total"/"Gym"; "Amount" (6) > "₱1.00" (5).
        assertEquals(listOf(7.0, 8.0), XlsxWriter.autoWidths(s))
    }

    @Test fun fileNames() {
        assertEquals("expenses-2026-10-16.csv", ExportFiles.dayName(LocalDate.of(2026, 10, 16), "csv"))
        assertEquals("expenses-2026-10.xlsx", ExportFiles.monthName(YearMonth.of(2026, 10), "xlsx"))
    }

    private fun parse(xml: String): Document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private fun children(e: Element, name: String): Int {
        var n = 0
        var c = e.firstChild
        while (c != null) { if (c is Element && c.localName == name) n++; c = c.nextSibling }
        return n
    }

    @Test fun xlsxPackageIsWellFormedAndConsistent() {
        val by = Sheet("By label", listOf("Label", "Amount"), listOf(listOf(Cell.Text("  leading & <b>"), Cell.Peso(100))), 0, listOf(1))
        val notes = Sheet(
            "Notes", listOf("Notes", "Amount"), listOf(listOf(Cell.Text("long\nnote"), Cell.Peso(1))), 0, listOf(1), wrapCols = setOf(0),
        )
        val parts = unzip(XlsxWriter.write(listOf(expensesSheet("  a\u0001b\u000Bc label"), by, notes)))
        val docs = parts.mapValues { parse(it.value) }

        // styles counts match children
        val styles = docs.getValue("xl/styles.xml").documentElement
        for ((container, child) in listOf("numFmts" to "numFmt", "fonts" to "font", "fills" to "fill", "borders" to "border", "cellXfs" to "xf", "cellStyleXfs" to "xf", "cellStyles" to "cellStyle")) {
            val el = styles.getElementsByTagNameNS("*", container).item(0) as Element
            assertEquals(container, el.getAttribute("count").toInt(), children(el, child))
        }

        // workbook r:ids resolve to rels, whose targets exist
        val relNs = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val rels = docs.getValue("xl/_rels/workbook.xml.rels").getElementsByTagNameNS("*", "Relationship")
        val relTargets = (0 until rels.length).associate { val r = rels.item(it) as Element; r.getAttribute("Id") to r.getAttribute("Target") }
        val sheetEls = docs.getValue("xl/workbook.xml").getElementsByTagNameNS("*", "sheet")
        assertEquals(3, sheetEls.length)
        for (i in 0 until sheetEls.length) {
            val id = (sheetEls.item(i) as Element).getAttributeNS(relNs, "id")
            val target = relTargets[id] ?: error("no relationship $id")
            assertTrue("missing xl/$target", "xl/$target" in parts)
        }

        // every part has a content type
        val ct = docs.getValue("[Content_Types].xml")
        val defaults = ct.getElementsByTagNameNS("*", "Default").let { n -> (0 until n.length).map { (n.item(it) as Element).getAttribute("Extension") }.toSet() }
        val overrides = ct.getElementsByTagNameNS("*", "Override").let { n -> (0 until n.length).map { (n.item(it) as Element).getAttribute("PartName") }.toSet() }
        for (name in parts.keys.filter { it != "[Content_Types].xml" }) {
            assertTrue("no content type for $name", "/$name" in overrides || name.substringAfterLast('.') in defaults)
        }

        // control chars removed, leading spaces preserved
        val sheet1 = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet1.contains("xml:space=\"preserve\">  abc label<"))
        assertTrue(parts.values.none { p -> p.any { it == '\u0001' || it == '\u000B' } })
    }

    @Test fun wrapColumnsUseWrapStyleAndCappedWidth() {
        val long = "x".repeat(200)
        val s = Sheet(
            name = "Expenses",
            header = listOf("Amount", "Notes"),
            rows = listOf(listOf(Cell.Peso(100), Cell.Text(long)), listOf(Cell.Peso(100), Cell.Blank)),
            totalLabelCol = 1,
            sumCols = listOf(0),
            wrapCols = setOf(1),
        )
        assertEquals(40.0, XlsxWriter.autoWidths(s)[1], 0.0)
        val parts = unzip(XlsxWriter.write(listOf(s)))
        val styles = parts.getValue("xl/styles.xml")
        assertTrue(styles, styles.contains("""<cellXfs count="7">"""))
        assertTrue(styles, styles.contains("""<alignment wrapText="1" vertical="top"/>"""))
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet, sheet.contains("""<c r="B2" s="6" t="inlineStr">"""))
        assertTrue("blank cell is omitted", !sheet.contains("""r="B3""""))
    }
}
