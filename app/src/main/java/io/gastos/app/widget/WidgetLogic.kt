package io.gastos.app.widget

import io.gastos.app.data.LabelEntity
import io.gastos.app.data.PaymentMethodEntity
import io.gastos.app.domain.AmountInput

/** Pure widget rules, kept out of Glance so they can be unit-tested. */
object WidgetLogic {
    /**
     * The label after [currentId] by position, wrapping to the first.
     * An unknown, deleted or null id yields the first. [labels] must not be empty.
     */
    fun nextLabel(labels: List<LabelEntity>, currentId: Long?): LabelEntity {
        val ordered = labels.sortedWith(compareBy({ it.position }, { it.id }))
        val index = ordered.indexOfFirst { it.id == currentId }
        return if (index < 0) ordered.first() else ordered[(index + 1) % ordered.size]
    }

    /**
     * The payment method after [currentId] by position, wrapping to the first.
     * An unknown, archived (absent from [methods]) or null id yields the first.
     * [methods] are the active methods and must not be empty.
     */
    fun nextMethod(methods: List<PaymentMethodEntity>, currentId: Long?): PaymentMethodEntity {
        val ordered = methods.sortedWith(compareBy({ it.position }, { it.id }))
        val index = ordered.indexOfFirst { it.id == currentId }
        return if (index < 0) ordered.first() else ordered[(index + 1) % ordered.size]
    }

    /** [name] cut to at most [max] characters, ending in "…" when cut, so a ticker pill keeps room for the amount. */
    fun shortName(name: String, max: Int): String =
        if (name.length <= max) name else name.take(max - 1).trimEnd() + "…"

    /** Amount font size (sp) for the top row: shrinks as [text] grows so it fits beside both tickers. */
    fun amountFontSp(text: String): Int = when {
        text.length <= 7 -> 22
        text.length <= 10 -> 18
        text.length <= 12 -> 15
        else -> 13
    }

    /** Centavos to save on Enter, or null when the input is empty, malformed or zero. */
    fun amountToSave(input: String): Long? = AmountInput.toCentavos(input)

    /** Input to show after a failed save of [typed]: the typed amount, unless the user already typed something new. */
    fun inputAfterFailedSave(current: String?, typed: String): String =
        if (current.isNullOrEmpty()) typed else current
}
