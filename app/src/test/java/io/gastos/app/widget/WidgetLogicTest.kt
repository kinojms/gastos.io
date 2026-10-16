package io.gastos.app.widget

import io.gastos.app.data.LabelEntity
import io.gastos.app.data.PaymentMethodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun pillInkIsWhiteOnDarkMethodColors() {
        assertEquals(WidgetLogic.WHITE, WidgetLogic.onMethodColor(0xFFA6192E.toInt())) // BPI
        assertEquals(WidgetLogic.WHITE, WidgetLogic.onMethodColor(0xFF0033A0.toInt())) // BDO
    }

    @Test fun pillInkIsDarkOnLightMethodColors() {
        assertEquals(WidgetLogic.DARK_INK, WidgetLogic.onMethodColor(0xFFFF6A13.toInt())) // Maribank
        assertEquals(WidgetLogic.DARK_INK, WidgetLogic.onMethodColor(0xFF00B26B.toInt())) // Maya
        assertEquals(WidgetLogic.DARK_INK, WidgetLogic.onMethodColor(0xFFFFFF00.toInt())) // yellow
        // Mid tones: GCash #007DFE (white 3.9:1 vs dark 4.7:1) and Cash #77808B (white 3.9:1 vs dark 4.6:1)
        // go dark under the higher-contrast rule.
        assertEquals(WidgetLogic.DARK_INK, WidgetLogic.onMethodColor(0xFF007DFE.toInt()))
        assertEquals(WidgetLogic.DARK_INK, WidgetLogic.onMethodColor(0xFF77808B.toInt()))
    }

    @Test fun narrowRowWithLongNamesStillFitsTheAmount() {
        val amount = "₱9,999,999.99"
        val fit = WidgetLogic.fitTopRow(250f, "Groceries", "Maribank", amount)
        assertTrue(fit.amountSp >= 12)
        val used = WidgetLogic.pillWidthDp(WidgetLogic.shortName("Groceries", fit.nameMax)) +
            WidgetLogic.pillWidthDp(WidgetLogic.shortName("Maribank", fit.nameMax)) +
            WidgetLogic.textWidthDp(amount, fit.amountSp)
        assertTrue("used $used", used <= WidgetLogic.topRowInnerDp(250f))
        assertTrue(WidgetLogic.topRowInnerDp(250f) - used + WidgetLogic.textWidthDp(amount, fit.amountSp) >= 90f)
    }

    @Test fun shortNamesKeepFullNamesAndLargestFont() {
        val fit = WidgetLogic.fitTopRow(250f, "Food", "Cash", "₱45.50")
        assertEquals(22, fit.amountSp)
        assertEquals("Food", WidgetLogic.shortName("Food", fit.nameMax))
    }

    @Test fun wideRowKeepsLongNamesWhole() {
        val fit = WidgetLogic.fitTopRow(350f, "Groceries", "Maribank", "₱9,999,999.99")
        assertEquals("Groceries", WidgetLogic.shortName("Groceries", fit.nameMax))
        assertTrue(fit.amountSp >= 15)
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
