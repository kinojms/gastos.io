package io.gastos.app.domain

/** Cleans the optional free-text fields of an expense: trimmed, blank becomes null, cut to the max length. */
object ExpenseText {
    const val DESCRIPTION_MAX = 60
    const val NOTES_MAX = 1000

    fun description(raw: String?): String? = clean(raw, DESCRIPTION_MAX)

    fun notes(raw: String?): String? = clean(raw, NOTES_MAX)

    private fun clean(raw: String?, max: Int): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.take(max)?.trimEnd()
}
