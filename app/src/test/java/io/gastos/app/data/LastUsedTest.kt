package io.gastos.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastUsedTest {
    private val methods = listOf("Cash", "GCash", "BDO").mapIndexed { i, name ->
        PaymentMethodEntity(id = (i + 1).toLong(), name = name, colorArgb = 0, position = i)
    }

    @Test fun picksLastUsedWhenActive() = assertEquals("GCash", LastUsed.method(methods, 2L)?.name)

    @Test fun archivedOrUnknownLastUsedFallsBackToFirst() = assertEquals("Cash", LastUsed.method(methods, 99L)?.name)

    @Test fun neverSetFallsBackToFirst() = assertEquals("Cash", LastUsed.method(methods, null)?.name)

    @Test fun noActiveMethodsGivesNull() = assertNull(LastUsed.method(emptyList(), 1L))
}
