package xyz.wallet.toolkit.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class UnsignedBytesTest {
    @Test
    fun encodesUnsignedDecimalValuesAsBigEndianWithoutASignByte() {
        assertContentEquals(byteArrayOf(0), "0000".decimalToUnsignedBytes())
        assertContentEquals(byteArrayOf(0xff.toByte()), "000255".decimalToUnsignedBytes())
        assertContentEquals(byteArrayOf(1, 0), "256".decimalToUnsignedBytes())
        assertContentEquals(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0), "18446744073709551616".decimalToUnsignedBytes())
        assertContentEquals(ByteArray(32) { 0xff.toByte() }, "115792089237316195423570985008687907853269984665640564039457584007913129639935".decimalToUnsignedBytes())
    }

    @Test
    fun encodesUnsignedLongValuesWithoutASignByte() {
        assertContentEquals(byteArrayOf(0), 0L.toUnsignedBytes())
        assertContentEquals(byteArrayOf(0xff.toByte()), 255L.toUnsignedBytes())
        assertContentEquals(byteArrayOf(1, 0), 256L.toUnsignedBytes())
        assertContentEquals(byteArrayOf(0x7f, -1, -1, -1, -1, -1, -1, -1), Long.MAX_VALUE.toUnsignedBytes())
    }

    @Test
    fun rejectsSignedEmptyWhitespaceAndNonAsciiDecimalValues() {
        listOf("-1", "+1", "", " 1", "1.0", "１", "1\n").forEach { value ->
            assertFailsWith<IllegalArgumentException> { value.decimalToUnsignedBytes() }
        }
        assertFailsWith<IllegalArgumentException> { (-1L).toUnsignedBytes() }
    }
}
