package io.gastos.app.export

import io.gastos.app.data.ExpenseRow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CsvExporterTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val header = "Date,Time,Label,Payment,Description,Amount,Notes"

    private fun row(
        label: String,
        centavos: Long,
        time: LocalTime = LocalTime.of(8, 10),
        payment: String = "Cash",
        description: String? = null,
        notes: String? = null,
    ) = ExpenseRow(
        1, centavos,
        LocalDate.of(2026, 10, 16).atTime(time).atZone(zone).toInstant().toEpochMilli(),
        1, label, 0,
        1, payment, 0, description, notes,
    )

    private fun text(bytes: ByteArray) = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)

    @Test fun csvColumns() {
        val out = text(
            CsvExporter.write(
                listOf(
                    row("Food", 18_500, LocalTime.of(12, 30), payment = "GCash", description = "Jollibee"),
                    row("Transpo", 30_000, LocalTime.of(13, 0)),
                ),
                zone,
            ),
        )
        assertEquals(
            listOf(header, "2026-10-16,12:30,Food,GCash,Jollibee,185.00,", "2026-10-16,13:00,Transpo,Cash,,300.00,", ",,,,Total,485.00,"),
            out.lines(),
        )
    }

    @Test fun csvHasBomHeaderRowsTotal() {
        val bytes = CsvExporter.write(listOf(row("Transpo", 4550)), zone)
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOf(3))
        assertEquals(
            listOf(header, "2026-10-16,08:10,Transpo,Cash,,45.50,", ",,,,Total,45.50,"),
            text(bytes).lines(),
        )
    }

    @Test fun csvQuotesSpecialChars() {
        val out = text(CsvExporter.write(listOf(row("Food, \"snacks\"", 100)), zone))
        assertEquals("2026-10-16,08:10,\"Food, \"\"snacks\"\"\",Cash,,1.00,", out.lines()[1])
    }

    @Test fun csvQuotesNotesWithCommaQuoteNewline() {
        val out = text(
            CsvExporter.write(listOf(row("Food", 100, description = "Milk, eggs", notes = "Said \"hi\",\nthen left")), zone),
        )
        assertEquals(
            "$header\n2026-10-16,08:10,Food,Cash,\"Milk, eggs\",1.00,\"Said \"\"hi\"\",\nthen left\"\n,,,,Total,1.00,",
            out,
        )
    }

    @Test fun csvEmptyStillHasHeaderAndZeroTotal() {
        assertEquals(listOf(header, ",,,,Total,0.00,"), text(CsvExporter.write(emptyList(), zone)).lines())
    }

    @Test fun csvUses24HourTime() {
        val out = text(CsvExporter.write(listOf(row("Food", 100, LocalTime.of(15, 5))), zone))
        assertEquals("2026-10-16,15:05,Food,Cash,,1.00,", out.lines()[1])
    }

    @Test fun csvQuotesNewlineInLabel() {
        val out = text(CsvExporter.write(listOf(row("Line1\nLine2", 100)), zone))
        assertEquals("$header\n2026-10-16,08:10,\"Line1\nLine2\",Cash,,1.00,\n,,,,Total,1.00,", out)
    }
}
