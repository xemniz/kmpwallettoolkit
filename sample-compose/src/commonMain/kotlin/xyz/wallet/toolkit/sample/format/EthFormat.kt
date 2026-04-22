package xyz.wallet.toolkit.sample.format

/**
 * Decimal ↔ wei conversion helpers for the sample-compose showcase.
 *
 * `weiHexToEthDecimal` is a display function and returns "—" on malformed
 * input; it never throws. `ethDecimalToWei` and `gweiToWei` parse user
 * input and throw `IllegalArgumentException` on malformed input so the
 * caller can surface a validation error.
 *
 * Pure-Kotlin string arithmetic so this compiles for iOS as well as JVM.
 */
object EthFormat {

    private const val WEI_DECIMALS = 18
    private const val GWEI_DECIMALS = 9
    private const val DISPLAY_SCALE = 6

    fun weiHexToEthDecimal(hex: String): String {
        val trimmed = hex.removePrefix("0x").removePrefix("0X")
        if (trimmed.isEmpty() || !trimmed.all { it.isHexDigit() }) return "—"
        val weiDecimal = hexToDecimalString(trimmed)
        return formatScaledDown(weiDecimal, WEI_DECIMALS, DISPLAY_SCALE)
    }

    fun ethDecimalToWei(decimal: String): String =
        scaleDecimalToInteger(decimal.trim(), WEI_DECIMALS, "ETH")

    fun gweiToWei(gwei: String): String =
        scaleDecimalToInteger(gwei.trim(), GWEI_DECIMALS, "Gwei")

    /** Multiply a non-negative decimal-integer string by a non-negative int. */
    fun multiplyWeiByInt(weiDecimal: String, factor: Int): String =
        multiplyDecimalByInt(weiDecimal, factor)

    /** Format a non-negative decimal-integer wei string as an ETH decimal with truncation. */
    fun formatWeiAsEth(weiDecimal: String, scale: Int = DISPLAY_SCALE): String =
        formatScaledDown(weiDecimal, WEI_DECIMALS, scale)

    /**
     * Compare two non-negative decimal strings (may contain a dot) numerically.
     * Returns -1 if a<b, 0 if equal, 1 if a>b. Returns null on malformed input.
     */
    fun compareDecimal(a: String, b: String): Int? {
        val ta = a.trim()
        val tb = b.trim()
        if (!isValidNonNegativeDecimal(ta) || !isValidNonNegativeDecimal(tb)) return null
        val (ai, af) = splitDecimal(ta)
        val (bi, bf) = splitDecimal(tb)
        val aiN = ai.trimStart('0').ifEmpty { "0" }
        val biN = bi.trimStart('0').ifEmpty { "0" }
        if (aiN.length != biN.length) return if (aiN.length < biN.length) -1 else 1
        val ic = aiN.compareTo(biN)
        if (ic != 0) return if (ic < 0) -1 else 1
        val maxLen = maxOf(af.length, bf.length)
        val afP = af.padEnd(maxLen, '0')
        val bfP = bf.padEnd(maxLen, '0')
        val fc = afP.compareTo(bfP)
        return when {
            fc < 0 -> -1
            fc > 0 -> 1
            else -> 0
        }
    }

    private fun splitDecimal(s: String): Pair<String, String> {
        val dot = s.indexOf('.')
        return if (dot < 0) s to "" else s.substring(0, dot) to s.substring(dot + 1)
    }

    // --- internals -------------------------------------------------------

    private fun scaleDecimalToInteger(input: String, decimals: Int, label: String): String {
        require(input.isNotEmpty()) { "$label decimal is empty" }
        require(isValidNonNegativeDecimal(input)) { "Invalid $label decimal: $input" }
        val dot = input.indexOf('.')
        val intPart = if (dot < 0) input else input.substring(0, dot)
        val fracPart = if (dot < 0) "" else input.substring(dot + 1)
        require(fracPart.length <= decimals) {
            "$label decimal has more than $decimals fraction digits: $input"
        }
        val padded = fracPart.padEnd(decimals, '0')
        val combined = (intPart + padded).trimStart('0')
        return combined.ifEmpty { "0" }
    }

    /** Format a non-negative decimal integer string as `unitDecimal` units, truncating to `scale`. */
    private fun formatScaledDown(decimalInt: String, unitDecimals: Int, scale: Int): String {
        val padded = decimalInt.padStart(unitDecimals + 1, '0')
        val splitAt = padded.length - unitDecimals
        val whole = padded.substring(0, splitAt).trimStart('0').ifEmpty { "0" }
        val frac = padded.substring(splitAt).take(scale).trimEnd('0')
        return if (frac.isEmpty()) whole else "$whole.$frac"
    }

    private fun hexToDecimalString(hex: String): String {
        var decimal = "0"
        for (ch in hex) {
            val digit = Character.digitSafe(ch)
            decimal = addDecimalStrings(multiplyDecimalByInt(decimal, 16), digit.toString())
        }
        return decimal
    }

    private fun multiplyDecimalByInt(decimal: String, factor: Int): String {
        if (decimal == "0" || factor == 0) return "0"
        val result = StringBuilder()
        var carry = 0
        for (i in decimal.length - 1 downTo 0) {
            val product = (decimal[i] - '0') * factor + carry
            result.append(product % 10)
            carry = product / 10
        }
        while (carry > 0) {
            result.append(carry % 10)
            carry /= 10
        }
        return result.reverse().toString().trimStart('0').ifEmpty { "0" }
    }

    private fun addDecimalStrings(a: String, b: String): String {
        val result = StringBuilder()
        var i = a.length - 1
        var j = b.length - 1
        var carry = 0
        while (i >= 0 || j >= 0 || carry > 0) {
            val da = if (i >= 0) a[i--] - '0' else 0
            val db = if (j >= 0) b[j--] - '0' else 0
            val sum = da + db + carry
            result.append(sum % 10)
            carry = sum / 10
        }
        return result.reverse().toString().trimStart('0').ifEmpty { "0" }
    }

    private fun isValidNonNegativeDecimal(input: String): Boolean {
        if (input.isEmpty()) return false
        var sawDot = false
        var sawDigit = false
        for (ch in input) {
            when {
                ch == '.' -> {
                    if (sawDot) return false
                    sawDot = true
                }
                ch in '0'..'9' -> sawDigit = true
                else -> return false
            }
        }
        return sawDigit
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private object Character {
        fun digitSafe(ch: Char): Int = when (ch) {
            in '0'..'9' -> ch - '0'
            in 'a'..'f' -> 10 + (ch - 'a')
            in 'A'..'F' -> 10 + (ch - 'A')
            else -> error("not hex: $ch")
        }
    }
}
