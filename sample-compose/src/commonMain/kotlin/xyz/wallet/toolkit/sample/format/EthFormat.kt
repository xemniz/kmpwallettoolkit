package xyz.wallet.toolkit.sample.format

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Decimal ↔ wei conversion helpers for the sample-compose showcase.
 *
 * `weiHexToEthDecimal` is a display function and returns "—" on malformed
 * input; it never throws. `ethDecimalToWei` and `gweiToWei` parse user
 * input and throw `IllegalArgumentException` on malformed input so the
 * caller can surface a validation error.
 *
 * Uses `java.math.*` — `sample-compose` is Android-only after S1 per
 * `sample-compose/build.gradle.kts`.
 */
object EthFormat {

    private val WEI_PER_ETH: BigDecimal = BigDecimal.TEN.pow(18)
    private val WEI_PER_GWEI: BigInteger = BigInteger.TEN.pow(9)
    private const val DISPLAY_SCALE = 6

    fun weiHexToEthDecimal(hex: String): String {
        return try {
            val trimmed = hex.removePrefix("0x").removePrefix("0X")
            if (trimmed.isEmpty() || !trimmed.all { it.isHexDigit() }) return "—"
            val wei = BigInteger(trimmed, 16)
            val eth = BigDecimal(wei).divide(WEI_PER_ETH, DISPLAY_SCALE, RoundingMode.DOWN)
            eth.stripTrailingZeros().toPlainString()
        } catch (_: NumberFormatException) {
            "—"
        } catch (_: ArithmeticException) {
            "—"
        }
    }

    fun ethDecimalToWei(decimal: String): String {
        val trimmed = decimal.trim()
        require(trimmed.isNotEmpty()) { "ETH decimal is empty" }
        val parsed = try {
            BigDecimal(trimmed)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid ETH decimal: $trimmed", e)
        }
        require(parsed.signum() >= 0) { "ETH decimal must be non-negative" }
        val wei = parsed.multiply(WEI_PER_ETH)
        require(wei.stripTrailingZeros().scale() <= 0) {
            "ETH decimal has more than 18 fraction digits: $trimmed"
        }
        return wei.toBigInteger().toString()
    }

    fun gweiToWei(gwei: String): String {
        val trimmed = gwei.trim()
        require(trimmed.isNotEmpty()) { "Gwei decimal is empty" }
        val parsed = try {
            BigDecimal(trimmed)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid gwei decimal: $trimmed", e)
        }
        require(parsed.signum() >= 0) { "Gwei decimal must be non-negative" }
        val wei = parsed.multiply(BigDecimal(WEI_PER_GWEI))
        require(wei.stripTrailingZeros().scale() <= 0) {
            "Gwei decimal has more than 9 fraction digits: $trimmed"
        }
        return wei.toBigInteger().toString()
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
