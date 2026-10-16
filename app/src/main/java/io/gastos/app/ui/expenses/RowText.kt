package io.gastos.app.ui.expenses

import io.gastos.app.data.ExpenseRow

/** Row wording: the description (when typed) is the title and pushes the label into the subtitle. */
object RowText {
    fun title(row: ExpenseRow): String = row.description ?: row.labelName

    fun subtitle(row: ExpenseRow, time: String): String =
        if (row.description != null) "${row.labelName} · ${row.paymentMethodName} · $time"
        else "${row.paymentMethodName} · $time"
}
