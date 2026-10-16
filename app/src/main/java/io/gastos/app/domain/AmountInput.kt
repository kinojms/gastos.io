package io.gastos.app.domain

sealed interface Key {
    data class Digit(val n: Int) : Key
    data object Dot : Key
    data object Backspace : Key
}

/** Pure string-state numpad logic shared by Home and the widget. */
object AmountInput {
    fun apply(current: String, key: Key): String = when (key) {
        is Key.Digit -> {
            val base = if (current == "0") "" else current
            val next = base + key.n
            val decimals = next.substringAfter('.', "")
            if (decimals.length > 2 || exceedsMax(next)) current else next
        }
        Key.Dot -> when {
            '.' in current -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        Key.Backspace -> current.dropLast(1)
    }

    /** Centavos for [text], or null when empty, malformed or zero. */
    fun toCentavos(text: String): Long? {
        val value = parse(text) ?: return null
        return if (value == 0L) null else value
    }

    /** Text shown on the numpad: "0" when empty, thousands grouped. */
    fun display(text: String): String {
        if (text.isEmpty()) return "0"
        val intPart = text.substringBefore('.')
        val rest = text.substring(intPart.length)
        return Money.group(intPart.ifEmpty { "0" }) + rest
    }

    private fun exceedsMax(text: String): Boolean {
        val value = parse(text) ?: return false
        return value > Money.MAX_CENTAVOS
    }

    private fun parse(text: String): Long? {
        if (text.isEmpty() || text == ".") return null
        val intPart = text.substringBefore('.')
        val decPart = text.substringAfter('.', "")
        if (decPart.length > 2) return null
        if (!intPart.all { it.isDigit() } || !decPart.all { it.isDigit() }) return null
        if (intPart.length > 12) return Long.MAX_VALUE
        val whole = if (intPart.isEmpty()) 0L else intPart.toLong()
        val frac = decPart.padEnd(2, '0').toLong()
        return whole * 100 + frac
    }
}
