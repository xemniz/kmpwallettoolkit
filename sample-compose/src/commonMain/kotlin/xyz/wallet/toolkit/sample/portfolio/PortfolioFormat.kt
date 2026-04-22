package xyz.wallet.toolkit.sample.portfolio

import kotlin.math.abs

/**
 * Formats a USD amount as `$#,###.##` (or `-$#,###.##`). No locale — the
 * showcase always renders en-US. Two decimals for |value| < 1000, thousand
 * separators beyond. Tiny positive amounts render as `<$0.01` so rows
 * never display as `$0.00`.
 */
fun formatUsd(value: Double): String {
    if (!value.isFinite()) return "$0.00"
    val sign = if (value < 0) "-" else ""
    val abs = abs(value)
    if (abs > 0 && abs < 0.01) return "${sign}<$0.01"

    val cents = (abs * 100).toLong()
    val whole = cents / 100
    val rem = (cents % 100).toInt()
    val wholeStr = buildString {
        val s = whole.toString()
        for (i in s.indices) {
            val posFromRight = s.length - i
            append(s[i])
            if (posFromRight > 1 && (posFromRight - 1) % 3 == 0) append(',')
        }
    }
    val remStr = if (rem < 10) "0$rem" else rem.toString()
    return "$sign\$$wholeStr.$remStr"
}

/**
 * Format a 24h percent delta, e.g. `+1.5%`, `-0.4%`, `0.0%`.
 */
fun formatPercent(percent: Double): String {
    val absValue = abs(percent)
    val whole = absValue.toLong()
    val tenth = ((absValue - whole) * 10 + 0.5).toLong().coerceIn(0, 9)
    val sign = when {
        percent > 0 -> "+"
        percent < 0 -> "-"
        else -> ""
    }
    return "$sign$whole.$tenth%"
}

/**
 * Trim a decimal string to at most 4 fractional digits with no trailing
 * zeros. The input is Zerion's `quantity.numeric`, which is already a
 * decimal string — we do not re-parse as Double to avoid precision loss.
 */
fun trimDecimal(numeric: String): String {
    if (numeric.isBlank()) return "0"
    val dot = numeric.indexOf('.')
    if (dot < 0) return numeric
    val whole = numeric.substring(0, dot)
    val frac = numeric.substring(dot + 1).take(4).trimEnd('0')
    return if (frac.isEmpty()) whole else "$whole.$frac"
}
