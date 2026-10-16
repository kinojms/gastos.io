package io.gastos.app.widget

import io.gastos.app.data.LabelEntity
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

    /** Centavos to save on Enter, or null when the input is empty, malformed or zero. */
    fun amountToSave(input: String): Long? = AmountInput.toCentavos(input)
}
