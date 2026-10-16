package io.gastos.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseTextTest {
    @Test fun descriptionTrimsAndNullsBlank() {
        assertNull(ExpenseText.description("   "))
        assertNull(ExpenseText.description(null))
        assertEquals("Jollibee", ExpenseText.description("  Jollibee "))
    }

    @Test fun descriptionCutsAt60() = assertEquals(60, ExpenseText.description("x".repeat(80))!!.length)

    @Test fun notesTrimAndNullBlank() {
        assertNull(ExpenseText.notes("\n  \t"))
        assertEquals("paid half", ExpenseText.notes(" paid half\n"))
    }

    @Test fun notesCutsAt1000() = assertEquals(1000, ExpenseText.notes("n".repeat(1200))!!.length)
}
