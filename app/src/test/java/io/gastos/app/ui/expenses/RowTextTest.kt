package io.gastos.app.ui.expenses

import io.gastos.app.data.ExpenseRow
import org.junit.Assert.assertEquals
import org.junit.Test

class RowTextTest {
    private fun row(description: String?) = ExpenseRow(
        id = 1, amountCentavos = 18_500, timestamp = 0, labelId = 1, labelName = "Food", labelColor = 0,
        paymentMethodId = 2, paymentMethodName = "GCash", paymentMethodColor = 0,
        description = description, notes = null,
    )

    @Test fun titleUsesDescriptionWhenPresent() {
        assertEquals("Jollibee", RowText.title(row("Jollibee")))
        assertEquals("Food · GCash · 12:30 PM", RowText.subtitle(row("Jollibee"), "12:30 PM"))
    }

    @Test fun titleFallsBackToLabel() {
        assertEquals("Food", RowText.title(row(null)))
        assertEquals("GCash · 12:30 PM", RowText.subtitle(row(null), "12:30 PM"))
    }
}
