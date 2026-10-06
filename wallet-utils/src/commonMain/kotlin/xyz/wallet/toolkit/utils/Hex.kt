package xyz.wallet.toolkit.utils

private val HEX_CHARS = "0123456789abcdef"

fun ByteArray.toHexString(prefix: Boolean = true): String {
    val chars = CharArray(size * 2)
    for (i in indices) {
        val value = this[i].toInt() and 0xFF
        chars[i * 2] = HEX_CHARS[value ushr 4]
        chars[i * 2 + 1] = HEX_CHARS[value and 0x0F]
    }
    val hex = chars.concatToString()
    return if (prefix) "0x$hex" else hex
}

fun String.hexToByteArray(): ByteArray {
    val normalized = removePrefix("0x")
    require(normalized.length % 2 == 0) { "Hex input must have an even length" }
    require(normalized.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        "Hex input must contain only hexadecimal digits"
    }

    return ByteArray(normalized.length / 2) { index ->
        val chunk = normalized.substring(index * 2, index * 2 + 2)
        chunk.toInt(16).toByte()
    }
}

