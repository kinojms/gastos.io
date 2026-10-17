package io.gastos.app.ui.expenses

import io.gastos.app.ui.expenses.SwipeRowLogic.Axis
import io.gastos.app.ui.expenses.SwipeRowLogic.OPEN_OFFSET_DP
import io.gastos.app.ui.expenses.SwipeRowLogic.dragOffset
import io.gastos.app.ui.expenses.SwipeRowLogic.lockAxis
import io.gastos.app.ui.expenses.SwipeRowLogic.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same cases as the PWA's swipeRow.test.ts, in dp. */
class SwipeRowLogicTest {
    @Test fun waitsForTheSlop() = assertNull(lockAxis(4f, 3f))
    @Test fun locksHorizontal() = assertEquals(Axis.H, lockAxis(12f, 3f))
    @Test fun locksVerticalForScrolling() = assertEquals(Axis.V, lockAxis(3f, 12f))
    @Test fun tiesGoToScrolling() = assertEquals(Axis.V, lockAxis(12f, 12f))
    @Test fun locksHorizontalLeftward() = assertEquals(Axis.H, lockAxis(-12f, 3f))

    @Test fun neverDragsRightPastClosed() = assertEquals(0f, dragOffset(0f, 50f))
    @Test fun rubberBandsPastOpen() = assertEquals(-160f, dragOffset(0f, -500f))
    @Test fun closesFromOpen() = assertEquals(0f, dragOffset(-144f, 200f))
    @Test fun followsTheFinger() = assertEquals(-60f, dragOffset(0f, -60f))

    @Test fun opensPastHalf() = assertEquals(OPEN_OFFSET_DP, settle(-80f, 0f))
    @Test fun snapsBackShortOfHalf() = assertEquals(0f, settle(-60f, 0f))
    @Test fun opensOnFlingLeft() = assertEquals(OPEN_OFFSET_DP, settle(-20f, -0.8f))
    @Test fun closesOnFlingRight() = assertEquals(0f, settle(-140f, 0.8f))
    @Test fun opens144Wide() = assertEquals(-144f, OPEN_OFFSET_DP)
}
