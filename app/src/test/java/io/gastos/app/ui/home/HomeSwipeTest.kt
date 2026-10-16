package io.gastos.app.ui.home

import io.gastos.app.ui.home.HomeSwipe.Axis
import io.gastos.app.ui.home.HomeSwipe.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSwipeTest {
    @Test fun tapIsNone() {
        assertEquals(Axis.NONE, HomeSwipe.decide(10f, 5f, 48f).axis)
    }

    @Test fun leftIsNextMethod() {
        assertEquals(Decision(Axis.METHOD, +1), HomeSwipe.decide(-60f, 10f, 48f))
    }

    @Test fun rightIsPreviousMethod() {
        assertEquals(Decision(Axis.METHOD, -1), HomeSwipe.decide(60f, -10f, 48f))
    }

    @Test fun upIsNextLabel() {
        assertEquals(Decision(Axis.LABEL, +1), HomeSwipe.decide(10f, -70f, 48f))
    }

    @Test fun downIsPreviousLabel() {
        assertEquals(Decision(Axis.LABEL, -1), HomeSwipe.decide(-10f, 70f, 48f))
    }

    @Test fun diagonalPicksDominant() {
        assertEquals(Axis.LABEL, HomeSwipe.decide(-60f, -80f, 48f).axis)
        assertEquals(Axis.METHOD, HomeSwipe.decide(-90f, 60f, 48f).axis)
    }

    @Test fun stepWraps() {
        val items = listOf("a", "b", "c", "d", "e")
        assertEquals(0, HomeSwipe.step(items, 4, +1))
        assertEquals(4, HomeSwipe.step(items, 0, -1))
        assertEquals(2, HomeSwipe.step(items, 1, +1))
    }

    @Test fun stepOnEmptyIsZero() {
        assertEquals(0, HomeSwipe.step(emptyList<String>(), 0, +1))
    }
}
