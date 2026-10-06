package xyz.wallet.toolkit.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class HexTest {
    @Test
    fun rejectsSignedPairsInsteadOfInterpretingThemAsHex() {
        listOf("0x-1", "+a", "01-1", "0x+a").forEach { input ->
            assertFailsWith<IllegalArgumentException> { input.hexToByteArray() }
        }
    }

    @Test
    fun acceptsUppercaseDigitsAndOptionalPrefix() {
        assertContentEquals(byteArrayOf(0, 0xab.toByte(), 0xff.toByte()), "0x00AbFF".hexToByteArray())
        assertContentEquals(byteArrayOf(0, 0xab.toByte(), 0xff.toByte()), "00AbFF".hexToByteArray())
        assertContentEquals(byteArrayOf(), "0x".hexToByteArray())
    }

    @Test
    fun rejectsOddLengthsAndNonHexCharacters() {
        listOf("0", "0xabc", "0xgg", " 1", "0x１２", "0x00\n").forEach { input ->
            assertFailsWith<IllegalArgumentException> { input.hexToByteArray() }
        }
    }
}
