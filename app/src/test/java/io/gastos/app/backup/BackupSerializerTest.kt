package io.gastos.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {
    private val sample = BackupFile(
        exportedAt = "2026-10-16T04:00:00Z",
        labels = listOf(
            BackupLabel(id = 1, name = "Food", colorArgb = 0xFFD9822B.toInt(), position = 0, isOther = false),
            BackupLabel(id = 5, name = "Other", colorArgb = 0xFF77808B.toInt(), position = 1, isOther = true),
        ),
        paymentMethods = listOf(
            BackupPaymentMethod(id = 1, name = "Cash", colorArgb = 0xFF77808B.toInt(), position = 0),
            BackupPaymentMethod(id = 2, name = "Old card", colorArgb = 0xFF0033A0.toInt(), position = 1, archived = true),
        ),
        expenses = listOf(
            BackupExpense(
                id = 7, amountCentavos = 4550, labelId = 1, timestamp = 1_760_000_000_000,
                paymentMethodId = 2, description = "Jollibee", notes = "With \"friends\",\nlate",
            ),
        ),
    )

    @Test fun roundTrip() {
        val json = BackupSerializer.encode(sample)
        assertTrue(json, json.contains("\"schemaVersion\":2"))
        assertEquals(sample, BackupSerializer.decode(json))
    }

    @Test fun rejectsNewerSchema() {
        val json = """{"schemaVersion":3,"exportedAt":"x","labels":[],"expenses":[],"futureField":{"a":1}}"""
        val e = assertThrows(UnsupportedBackupException::class.java) { BackupSerializer.decode(json) }
        assertEquals("This backup is from a newer version of Gastos.io. Update the app first.", e.message)
    }

    @Test fun ignoresUnknownKeys() {
        val json = """{"schemaVersion":1,"exportedAt":"2026-10-16T04:00:00Z","device":"S25",
            "labels":[{"id":1,"name":"Food","colorArgb":-2522581,"position":0,"isOther":false,"emoji":"x"}],
            "expenses":[{"id":2,"amountCentavos":100,"labelId":1,"timestamp":5,"note":"n"}]}"""
        val f = BackupSerializer.decode(json)
        assertEquals("Food", f.labels.single().name)
        assertEquals(100L, f.expenses.single().amountCentavos)
    }

    @Test fun decodesSchema1WithoutMethods() {
        val json = """{"schemaVersion":1,"exportedAt":"2026-10-16T04:00:00Z",
            "labels":[{"id":1,"name":"Other","colorArgb":1,"position":0,"isOther":true}],
            "expenses":[{"id":2,"amountCentavos":100,"labelId":1,"timestamp":5}]}"""
        val f = BackupSerializer.decode(json)
        assertEquals(1, f.schemaVersion)
        assertEquals(emptyList<BackupPaymentMethod>(), f.paymentMethods)
        assertEquals(BackupExpense(id = 2, amountCentavos = 100, labelId = 1, timestamp = 5), f.expenses.single())
    }
}
