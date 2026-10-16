package io.gastos.app.widget

import io.gastos.app.data.LabelEntity
import io.gastos.app.data.PaymentMethodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetLogicTest {
    private val labels = listOf("Food", "Transpo", "Hobbies", "Bills", "Other").mapIndexed { i, name ->
        LabelEntity(id = (i + 1).toLong(), name = name, colorArgb = 0, position = i, isOther = name == "Other")
    }

    private val methods = listOf("Cash", "GCash", "Maribank", "BPI", "Maya", "BDO").mapIndexed { i, name ->
        PaymentMethodEntity(id = (i + 1).toLong(), name = name, colorArgb = 0, position = i)
    }

    @Test fun methodsCycleInOrderAndWrap() {
        val seen = mutableListOf<String>()
        var current: Long? = methods.first().id
        repeat(6) {
            val next = WidgetLogic.nextMethod(methods, current)
            seen += next.name
            current = next.id
        }
        assertEquals(listOf("GCash", "Maribank", "BPI", "Maya", "BDO", "Cash"), seen)
    }

    @Test fun methodsFollowPositionNotListOrder() =
        assertEquals("Maribank", WidgetLogic.nextMethod(methods.reversed(), methods[1].id).name)

    @Test fun archivedCurrentMethodFallsBackToFirst() {
        // An archived method is not in the active list the widget passes in.
        val active = methods.filterNot { it.name == "BPI" }
        assertEquals("Cash", WidgetLogic.nextMethod(active, methods[3].id).name)
    }

    @Test fun unknownOrNullCurrentMethodFallsBackToFirst() {
        assertEquals("Cash", WidgetLogic.nextMethod(methods, 999L).name)
        assertEquals("Cash", WidgetLogic.nextMethod(methods, null).name)
    }

    @Test fun singleMethodWrapsToItself() =
        assertEquals("Cash", WidgetLogic.nextMethod(listOf(methods.first()), methods.first().id).name)

    @Test fun shortNameKeepsNamesThatFit() {
        assertEquals("Maribank", WidgetLogic.shortName("Maribank", 8))
        assertEquals("BDO", WidgetLogic.shortName("BDO", 8))
    }

    @Test fun shortNameEllipsizesLongNames() =
        assertEquals("Security…", WidgetLogic.shortName("Security Bank", 9))

    @Test fun amountFontShrinksAsTextGrows() {
        assertEquals(22, WidgetLogic.amountFontSp("₱45.50"))
        assertEquals(18, WidgetLogic.amountFontSp("₱1,234.56"))
        assertEquals(15, WidgetLogic.amountFontSp("₱1,234,567.8"))
        assertEquals(13, WidgetLogic.amountFontSp("₱9,999,999.99"))
    }

    @Test fun cyclesInOrderAndWraps() {
        val seen = mutableListOf<String>()
        var current: Long? = labels.first().id
        repeat(5) {
            val next = WidgetLogic.nextLabel(labels, current)
            seen += next.name
            current = next.id
        }
        assertEquals(listOf("Transpo", "Hobbies", "Bills", "Other", "Food"), seen)
    }

    @Test fun followsPositionNotListOrder() {
        val next = WidgetLogic.nextLabel(labels.reversed(), labels[1].id)
        assertEquals("Hobbies", next.name)
    }

    @Test fun deletedCurrentIdFallsBackToFirst() =
        assertEquals("Food", WidgetLogic.nextLabel(labels, 999L).name)

    @Test fun nullCurrentIdFallsBackToFirst() =
        assertEquals("Food", WidgetLogic.nextLabel(labels, null).name)

    @Test fun singleLabelWrapsToItself() =
        assertEquals("Other", WidgetLogic.nextLabel(listOf(labels.last()), labels.last().id).name)

    @Test fun failedSaveRestoresIntoEmptyInput() {
        assertEquals("12.5", WidgetLogic.inputAfterFailedSave(null, "12.5"))
        assertEquals("12.5", WidgetLogic.inputAfterFailedSave("", "12.5"))
    }

    @Test fun failedSaveKeepsNewTyping() =
        assertEquals("7", WidgetLogic.inputAfterFailedSave("7", "12.5"))

    @Test fun emptyInputSavesNothing() = assertNull(WidgetLogic.amountToSave(""))

    @Test fun zeroInputSavesNothing() = assertNull(WidgetLogic.amountToSave("0.00"))

    @Test fun dotOnlySavesNothing() = assertNull(WidgetLogic.amountToSave("."))

    @Test fun amountSavedInCentavos() = assertEquals(4550L, WidgetLogic.amountToSave("45.5"))
}
