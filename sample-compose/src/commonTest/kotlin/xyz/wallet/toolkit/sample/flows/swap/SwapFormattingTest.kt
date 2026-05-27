package xyz.wallet.toolkit.sample.flows.swap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwapFormattingTest {
    @Test
    fun amountToRawConvertsDecimalInput() {
        assertEquals("1250000000000000000", amountToRaw("1.25", 18))
        assertEquals("1", amountToRaw("0.000001", 6))
        assertEquals("1000000", amountToRaw("1", 6))
    }

    @Test
    fun amountToRawRejectsInvalidOrZeroInput() {
        assertNull(amountToRaw("", 18))
        assertNull(amountToRaw("0", 18))
        assertNull(amountToRaw("1.0000001", 6))
        assertNull(amountToRaw("1.2.3", 18))
    }

    @Test
    fun rawToAmountTrimsDisplayDecimals() {
        assertEquals("1.25", rawToAmount("1250000000000000000", 18))
        assertEquals("0.000001", rawToAmount("1", 6))
        assertEquals("123", rawToAmount("123000000", 6))
    }
}

