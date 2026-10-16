package io.gastos.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {
    @Test fun digitsAppend() = assertEquals("45", AmountInput.apply("4", Key.Digit(5)))
    @Test fun leadingZeroReplaced() = assertEquals("7", AmountInput.apply("0", Key.Digit(7)))
    @Test fun dotFirstBecomesZeroDot() = assertEquals("0.", AmountInput.apply("", Key.Dot))
    @Test fun secondDotIgnored() = assertEquals("4.5", AmountInput.apply("4.5", Key.Dot))
    @Test fun thirdDecimalIgnored() = assertEquals("4.55", AmountInput.apply("4.55", Key.Digit(1)))
    @Test fun overMaxIgnored() = assertEquals("9999999", AmountInput.apply("9999999", Key.Digit(1)))
    @Test fun backspace() = assertEquals("4.", AmountInput.apply("4.5", Key.Backspace))
    @Test fun backspaceEmpty() = assertEquals("", AmountInput.apply("", Key.Backspace))
    @Test fun toCentavos() {
        assertEquals(4550L, AmountInput.toCentavos("45.5"))
        assertEquals(4500L, AmountInput.toCentavos("45."))
        assertNull(AmountInput.toCentavos(""))
        assertNull(AmountInput.toCentavos("0.00"))
    }
    @Test fun displayEmptyIsZero() = assertEquals("0", AmountInput.display(""))

    @Test fun zeroOnZeroStaysSingle() = assertEquals("0", AmountInput.apply("0", Key.Digit(0)))
    @Test fun digitAfterZeroDotAppends() = assertEquals("0.5", AmountInput.apply("0.", Key.Digit(5)))
    @Test fun maxExactlyAllowed() = assertEquals("9999999.99", AmountInput.apply("9999999.9", Key.Digit(9)))
    @Test fun overMaxByCentavoIgnored() = assertEquals("9999999.99", AmountInput.apply("9999999.99", Key.Digit(1)))
    @Test fun dotAtMaxIntegerAllowed() = assertEquals("9999999.", AmountInput.apply("9999999", Key.Dot))
    @Test fun toCentavosMax() = assertEquals(Money.MAX_CENTAVOS, AmountInput.toCentavos("9999999.99"))
    @Test fun toCentavosDotOnlyIsNull() = assertNull(AmountInput.toCentavos("."))
    @Test fun displayGroupsThousands() = assertEquals("1,234.5", AmountInput.display("1234.5"))
    @Test fun displayKeepsTrailingDot() = assertEquals("45.", AmountInput.display("45."))
}
