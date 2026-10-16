package io.gastos.app.widget

import io.gastos.app.data.LabelEntity
import io.gastos.app.data.PaymentMethodEntity
import androidx.compose.ui.graphics.Color
import io.gastos.app.domain.AmountInput
import io.gastos.app.ui.theme.contrastRatio

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

    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val DARK_INK: Int = 0xFF1C1300.toInt()

    /** Pill text color on a method color: white or dark ink, whichever has the higher WCAG contrast. */
    fun onMethodColor(argb: Int): Int {
        val bg = Color(argb or 0xFF000000.toInt()) // pills are opaque
        return if (contrastRatio(Color(WHITE), bg) >= contrastRatio(Color(DARK_INK), bg)) WHITE else DARK_INK
    }

    /** How the top row fits: the most name characters per pill, and the amount font (sp). */
    data class TopRowFit(val nameMax: Int, val amountSp: Int)

    private const val MIN_AMOUNT_DP = 90f
    private const val NAME_SP = 12
    private const val CHEVRON_SP = 11
    private const val PILL_PADDING_DP = 16f // 8dp each side
    private val NAME_CAPS = 12 downTo 4
    private val AMOUNT_SPS = listOf(22, 18, 15, 13, 12)

    /**
     * Width for pills plus amount in a [widgetWidthDp]-wide widget: minus the outer padding (2 × 9dp),
     * the row's own padding (2 × 3dp), the gap between pills (4dp) and the amount's start padding (4dp).
     */
    fun topRowInnerDp(widgetWidthDp: Float): Float = widgetWidthDp - 18f - 6f - 4f - 4f

    /**
     * Fits both ticker pills and the amount in one row. Names keep as many characters as allow
     * the amount at least [MIN_AMOUNT_DP] (shortest cap 4), then the amount takes the largest font that fits.
     */
    fun fitTopRow(widgetWidthDp: Float, labelName: String, methodName: String, amount: String): TopRowFit {
        val inner = topRowInnerDp(widgetWidthDp)
        var cap = NAME_CAPS.last
        var avail = 0f
        for (c in NAME_CAPS) {
            cap = c
            avail = inner - pillWidthDp(shortName(labelName, c)) - pillWidthDp(shortName(methodName, c))
            if (avail >= MIN_AMOUNT_DP) break
        }
        val sp = AMOUNT_SPS.firstOrNull { textWidthDp(amount, it) <= avail } ?: AMOUNT_SPS.last()
        return TopRowFit(cap, sp)
    }

    /** Estimated width of a ticker pill showing [name]: padding, name and trailing " ›". */
    fun pillWidthDp(name: String): Float =
        PILL_PADDING_DP + textWidthDp(name, NAME_SP) + textWidthDp(" ›", CHEVRON_SP)

    /** Rough rendered width of [text] at [sp] (1sp ≈ 1dp at default font scale), from per-character em widths. */
    fun textWidthDp(text: String, sp: Int): Float = text.sumOf { charEm(it) }.toFloat() * sp

    private fun charEm(c: Char): Double = when {
        c in ".,:;'!|ilj " -> 0.32
        c == '…' -> 0.9
        c == '₱' || c.isUpperCase() -> 0.68
        c.isDigit() -> 0.58
        c == 'm' || c == 'w' -> 0.85
        else -> 0.56
    }

    /** Centavos to save on Enter, or null when the input is empty, malformed or zero. */
    fun amountToSave(input: String): Long? = AmountInput.toCentavos(input)

    /** Input to show after a failed save of [typed]: the typed amount, unless the user already typed something new. */
    fun inputAfterFailedSave(current: String?, typed: String): String =
        if (current.isNullOrEmpty()) typed else current
}
