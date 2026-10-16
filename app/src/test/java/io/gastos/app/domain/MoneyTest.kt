package io.gastos.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun formatPeso() {
        assertEquals("₱45.50", Money.format(4550))
        assertEquals("₱1,234.50", Money.format(123450))
        assertEquals("₱0.00", Money.format(0))
        assertEquals("45.50", Money.plain(4550))
    }

    @Test fun formatMaxAndSmall() {
        assertEquals("₱9,999,999.99", Money.format(Money.MAX_CENTAVOS))
        assertEquals("₱0.05", Money.format(5))
        assertEquals("₱1,000.00", Money.format(100000))
    }

    @Test fun plainHasNoGrouping() = assertEquals("1234.50", Money.plain(123450))
}
