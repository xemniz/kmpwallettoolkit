package xyz.wallet.toolkit.sample.flows.swap

/**
 * ABI helpers for the two ERC-20 calls the swap flow needs: read `allowance`
 * and encode `approve`. Kept string-based — no BigInteger, no multiplatform
 * arithmetic library — because all inputs are either known-short (function
 * selectors, addresses) or already-decimal strings from 0x.
 */
internal object Erc20 {

    private const val SELECTOR_ALLOWANCE = "dd62ed3e"
    private const val SELECTOR_APPROVE = "095ea7b3"

    /**
     * `allowance(owner, spender)` calldata — 4-byte selector + two 32-byte
     * address words. Inputs accept either 0x-prefixed or bare hex addresses.
     */
    fun allowanceCallData(owner: String, spender: String): String {
        val ownerWord = padAddress(owner)
        val spenderWord = padAddress(spender)
        return "0x$SELECTOR_ALLOWANCE$ownerWord$spenderWord"
    }

    /**
     * `approve(spender, amount)` calldata. [amount] is a decimal string.
     */
    fun approveCallData(spender: String, amount: String): String {
        val spenderWord = padAddress(spender)
        val amountWord = padAmount(amount)
        return "0x$SELECTOR_APPROVE$spenderWord$amountWord"
    }

    const val MAX_UINT256_DECIMAL: String =
        "115792089237316195423570985008687907853269984665640564039457584007913129639935"

    /**
     * Decodes a `0x`-prefixed uint256 hex into a decimal string. Returns
     * `null` on malformed input — callers treat that as "allowance unknown,
     * proceed as if zero".
     */
    fun decodeUint256Decimal(hex: String): String? {
        val stripped = when {
            hex.startsWith("0x") || hex.startsWith("0X") -> hex.substring(2)
            else -> return null
        }
        if (stripped.isEmpty()) return null
        if (!stripped.all { it.isHexChar() }) return null
        return hexToDecimal(stripped)
    }

    private fun padAddress(address: String): String {
        val stripped = address.removePrefix("0x").removePrefix("0X").lowercase()
        require(stripped.length == 40 && stripped.all { it.isHexChar() }) {
            "Malformed address"
        }
        return "0".repeat(24) + stripped
    }

    private fun padAmount(decimal: String): String {
        val hex = decimalToHex(decimal)
        require(hex.length <= 64) { "amount exceeds uint256" }
        return "0".repeat(64 - hex.length) + hex
    }

    // --- string-based decimal <-> hex ---

    private fun decimalToHex(decimal: String): String {
        if (decimal.isEmpty() || decimal == "0") return "0"
        require(decimal.all { it in '0'..'9' }) { "non-decimal input" }
        var digits = decimal.map { it - '0' }.toIntArray()
        val out = StringBuilder()
        while (digits.any { it != 0 }) {
            var remainder = 0
            val next = IntArray(digits.size)
            for (i in digits.indices) {
                val cur = remainder * 10 + digits[i]
                next[i] = cur / 16
                remainder = cur % 16
            }
            out.append("0123456789abcdef"[remainder])
            digits = next
        }
        return out.reverse().toString().trimStart('0').ifEmpty { "0" }
    }

    private fun hexToDecimal(hex: String): String {
        if (hex.isEmpty()) return "0"
        val nibbles = hex.map { hexValue(it) }.toIntArray()
        var digits = IntArray(1) { 0 }
        for (nib in nibbles) {
            // digits = digits * 16 + nib
            var carry = nib
            for (i in digits.indices.reversed()) {
                val v = digits[i] * 16 + carry
                digits[i] = v % 10
                carry = v / 10
            }
            while (carry > 0) {
                digits = IntArray(digits.size + 1).also {
                    it[0] = carry % 10
                    for (j in digits.indices) it[j + 1] = digits[j]
                }
                carry /= 10
            }
        }
        return digits.joinToString(separator = "") { it.toString() }.trimStart('0').ifEmpty { "0" }
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> error("non-hex char")
    }
}

private fun Char.isHexChar(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * Convert decimal string to 0x-prefixed hex — used when building
 * `eth_call` payloads from numeric fields 0x returns as decimal.
 */
internal fun decimalToHex0x(decimal: String): String {
    if (decimal.isEmpty() || decimal == "0") return "0x0"
    return "0x" + decimalToHexNoPrefix(decimal)
}

private fun decimalToHexNoPrefix(decimal: String): String {
    require(decimal.all { it in '0'..'9' }) { "non-decimal input" }
    var digits = decimal.map { it - '0' }.toIntArray()
    val out = StringBuilder()
    while (digits.any { it != 0 }) {
        var remainder = 0
        val next = IntArray(digits.size)
        for (i in digits.indices) {
            val cur = remainder * 10 + digits[i]
            next[i] = cur / 16
            remainder = cur % 16
        }
        out.append("0123456789abcdef"[remainder])
        digits = next
    }
    return out.reverse().toString().trimStart('0').ifEmpty { "0" }
}

/**
 * Compare two decimal-string non-negative integers: 1 if a>b, 0 if equal, -1 if a<b.
 */
internal fun compareDecimal(a: String, b: String): Int {
    val ax = a.trimStart('0').ifEmpty { "0" }
    val bx = b.trimStart('0').ifEmpty { "0" }
    if (ax.length != bx.length) return if (ax.length > bx.length) 1 else -1
    return ax.compareTo(bx).let { if (it > 0) 1 else if (it < 0) -1 else 0 }
}
