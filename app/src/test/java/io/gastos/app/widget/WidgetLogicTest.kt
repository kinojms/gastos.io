package io.gastos.app.widget

import io.gastos.app.data.LabelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetLogicTest {
    private val labels = listOf("Food", "Transpo", "Hobbies", "Bills", "Other").mapIndexed { i, name ->
        LabelEntity(id = (i + 1).toLong(), name = name, colorArgb = 0, position = i, isOther = name == "Other")
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

    @Test fun emptyInputSavesNothing() = assertNull(WidgetLogic.amountToSave(""))

    @Test fun zeroInputSavesNothing() = assertNull(WidgetLogic.amountToSave("0.00"))

    @Test fun dotOnlySavesNothing() = assertNull(WidgetLogic.amountToSave("."))

    @Test fun amountSavedInCentavos() = assertEquals(4550L, WidgetLogic.amountToSave("45.5"))
}
