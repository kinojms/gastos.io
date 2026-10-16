package io.gastos.app.data

/** Shared "last-used" pick rule, used by AppPrefs (Enter, Home) and the widget's render. */
object LastUsed {
    /** The method with [lastId] if it is still in [active], else the first active one; null when [active] is empty. */
    fun method(active: List<PaymentMethodEntity>, lastId: Long?): PaymentMethodEntity? =
        active.firstOrNull { it.id == lastId } ?: active.firstOrNull()
}
