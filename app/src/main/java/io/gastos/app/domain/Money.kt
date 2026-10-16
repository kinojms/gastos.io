package io.gastos.app.domain

object Money {
    const val MAX_CENTAVOS = 999_999_999L

    /** Locale-independent: "₱1,234.50". */
    fun format(centavos: Long): String {
        val sign = if (centavos < 0) "-" else ""
        val abs = if (centavos < 0) -centavos else centavos
        return "$sign₱${group((abs / 100).toString())}.${twoDigits(abs % 100)}"
    }

    /** No symbol, no grouping: "1234.50" (for CSV/XLSX). */
    fun plain(centavos: Long): String {
        val sign = if (centavos < 0) "-" else ""
        val abs = if (centavos < 0) -centavos else centavos
        return "$sign${abs / 100}.${twoDigits(abs % 100)}"
    }

    internal fun group(digits: String): String =
        digits.reversed().chunked(3).joinToString(",").reversed()

    private fun twoDigits(n: Long): String = n.toString().padStart(2, '0')
}
