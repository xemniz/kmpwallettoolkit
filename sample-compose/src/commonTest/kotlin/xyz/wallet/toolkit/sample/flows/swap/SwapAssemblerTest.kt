package xyz.wallet.toolkit.sample.flows.swap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwapAssemblerTest {
    @Test
    fun parseHexLongRequiresZeroXPrefix() {
        assertEquals(0L, parseHexLong("0x0"))
        assertEquals(26L, parseHexLong("0x1a"))
        assertEquals(26L, parseHexLong("0X1A"))
        assertNull(parseHexLong("1a"))
    }

    @Test
    fun parseHexLongRejectsMalformedInput() {
        assertNull(parseHexLong(""))
        assertNull(parseHexLong("0x"))
        assertNull(parseHexLong("0xzz"))
    }
}

