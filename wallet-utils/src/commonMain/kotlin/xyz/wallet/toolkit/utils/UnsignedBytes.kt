package xyz.wallet.toolkit.utils

/** Encodes a nonnegative value as minimal big-endian bytes; zero is a single zero byte. */
fun Long.toUnsignedBytes(): ByteArray {
    require(this >= 0) { "Unsigned values must not be negative" }
    if (this == 0L) return byteArrayOf(0)
    var current = this
    val bytes = mutableListOf<Byte>()
    while (current != 0L) {
        bytes += (current and 0xff).toByte()
        current = current ushr 8
    }
    return bytes.asReversed().toByteArray()
}

/** Decimal digits only, with leading zeroes accepted. Zero is encoded as a single zero byte. */
fun String.decimalToUnsignedBytes(): ByteArray {
    requireUnsignedDecimal()
    if (all { it == '0' }) return byteArrayOf(0)

    val digits = map { it - '0' }.toMutableList()
    val bytes = mutableListOf<Byte>()
    while (digits.any { it != 0 }) {
        var remainder = 0
        for (i in digits.indices) {
            val current = remainder * 10 + digits[i]
            digits[i] = current / 256
            remainder = current % 256
        }
        bytes += remainder.toByte()
        while (digits.size > 1 && digits.first() == 0) digits.removeAt(0)
    }
    return bytes.asReversed().toByteArray()
}

fun String.requireUnsignedDecimal() {
    require(isNotEmpty() && all { it in '0'..'9' }) { "Unsigned decimal values must contain only digits" }
}
