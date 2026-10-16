package io.gastos.app.export

import io.gastos.app.domain.Money
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal hand-written OOXML workbook writer. */
object XlsxWriter {
    sealed interface Cell {
        data class Text(val s: String) : Cell
        data class Peso(val centavos: Long) : Cell
        data class Date(val date: LocalDate) : Cell
    }

    data class Sheet(
        val name: String,
        val header: List<String>,
        val rows: List<List<Cell>>,
        val totalLabelCol: Int,
        val sumCols: List<Int>,
        val colWidths: List<Double>,
    )

    // Style indexes in cellXfs.
    private const val S_DEFAULT = 0
    private const val S_HEADER = 1
    private const val S_PESO = 2
    private const val S_DATE = 3
    private const val S_TOTAL_TEXT = 4
    private const val S_TOTAL_PESO = 5

    private const val XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun write(sheets: List<Sheet>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", rootRels())
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", styles())
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheet(s)) }
        }
        return bos.toByteArray()
    }

    private fun contentTypes(n: Int) = buildString {
        append(XML)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private fun rootRels() = XML +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(sheets: List<Sheet>) = buildString {
        append(XML)
        append("""<workbook xmlns="$NS" xmlns:r="$REL_NS"><sheets>""")
        sheets.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append(XML)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) append("""<Relationship Id="rId$i" Type="$REL_NS/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${n + 1}" Type="$REL_NS/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun styles() = XML +
        """<styleSheet xmlns="$NS">""" +
        """<numFmts count="2"><numFmt numFmtId="164" formatCode="&quot;₱&quot;#,##0.00"/><numFmt numFmtId="165" formatCode="yyyy-mm-dd"/></numFmts>""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="6">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """<xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>""" +
        """</cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        """</styleSheet>"""

    private fun sheet(s: Sheet) = buildString {
        append(XML)
        append("""<worksheet xmlns="$NS"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" state="frozen"/></sheetView></sheetViews>""")
        if (s.colWidths.isNotEmpty()) {
            append("<cols>")
            s.colWidths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        append("""<row r="1">""")
        s.header.forEachIndexed { c, h -> append(textCell(ref(c, 1), h, S_HEADER)) }
        append("</row>")
        s.rows.forEachIndexed { i, row ->
            val r = i + 2
            append("""<row r="$r">""")
            row.forEachIndexed { c, cell ->
                when (cell) {
                    is Cell.Text -> append(textCell(ref(c, r), cell.s, S_DEFAULT))
                    is Cell.Peso -> append("""<c r="${ref(c, r)}" s="$S_PESO"><v>${Money.plain(cell.centavos)}</v></c>""")
                    is Cell.Date -> append("""<c r="${ref(c, r)}" s="$S_DATE"><v>${cell.date.toEpochDay() + 25569}</v></c>""")
                }
            }
            append("</row>")
        }
        val tr = s.rows.size + 2
        append("""<row r="$tr">""")
        val cols = (s.sumCols + s.totalLabelCol).distinct().sorted()
        for (c in cols) {
            if (c == s.totalLabelCol) {
                append(textCell(ref(c, tr), "Total", S_TOTAL_TEXT))
            } else {
                val sum = s.rows.sumOf { (it.getOrNull(c) as? Cell.Peso)?.centavos ?: 0L }
                val col = colName(c)
                val v = Money.plain(sum)
                if (s.rows.isEmpty()) {
                    append("""<c r="${ref(c, tr)}" s="$S_TOTAL_PESO"><v>$v</v></c>""")
                } else {
                    append("""<c r="${ref(c, tr)}" s="$S_TOTAL_PESO"><f>SUM(${col}2:$col${tr - 1})</f><v>$v</v></c>""")
                }
            }
        }
        append("</row></sheetData></worksheet>")
    }

    private fun textCell(ref: String, text: String, style: Int) =
        """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${esc(text)}</t></is></c>"""

    private fun colName(c: Int): String {
        var n = c + 1
        val sb = StringBuilder()
        while (n > 0) {
            val m = (n - 1) % 26
            sb.insert(0, 'A' + m)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun ref(c: Int, r: Int) = "${colName(c)}$r"

    /** Escapes XML special characters and drops characters illegal in XML 1.0. */
    private fun esc(s: String): String = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch == '\'' -> append("&apos;")
            ch < ' ' && ch != '\n' && ch != '\r' && ch != '\t' -> {}
            else -> append(ch)
        }
    }
}
