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

    private fun row(label: String, centavos: Long, time: LocalTime = LocalTime.of(8, 10)) = ExpenseRow(
        1, centavos,
        LocalDate.of(2026, 10, 16).atTime(time).atZone(zone).toInstant().toEpochMilli(),
        1, label, 0,
    )

    private fun text(bytes: ByteArray) = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)

    @Test fun csvHasBomHeaderRowsTotal() {
        val bytes = CsvExporter.write(listOf(row("Transpo", 4550)), zone)
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOf(3))
        assertEquals(
            listOf("Date,Time,Label,Amount", "2026-10-16,08:10,Transpo,45.50", ",,Total,45.50"),
            text(bytes).lines(),
        )
    }

    @Test fun csvQuotesSpecialChars() {
        val out = text(CsvExporter.write(listOf(row("Food, \"snacks\"", 100)), zone))
        assertEquals("2026-10-16,08:10,\"Food, \"\"snacks\"\"\",1.00", out.lines()[1])
    }

    @Test fun csvEmptyStillHasHeaderAndZeroTotal() {
        assertEquals(listOf("Date,Time,Label,Amount", ",,Total,0.00"), text(CsvExporter.write(emptyList(), zone)).lines())
    }

    @Test fun csvUses24HourTime() {
        val out = text(CsvExporter.write(listOf(row("Food", 100, LocalTime.of(15, 5))), zone))
        assertEquals("2026-10-16,15:05,Food,1.00", out.lines()[1])
    }
}
