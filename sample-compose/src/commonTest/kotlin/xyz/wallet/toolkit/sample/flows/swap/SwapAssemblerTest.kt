package xyz.wallet.toolkit.sample.flows.swap

import xyz.wallet.toolkit.core.SupportedChain
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

    @Test
    fun swapTransactionUsesZeroExGasPriceTransactionShape() {
        val tx = QuoteTransaction(
            to = "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD",
            dataHex = "0x2213bc0b",
            valueWei = "123",
            gasLimit = "832055",
            gasPriceWei = "1676486955",
        ).toSwapTransaction(SupportedChain.Ethereum, nonce = 42)

        assertEquals(1L, tx.chainId)
        assertEquals("0xabcdefabcdefabcdefabcdefabcdefabcdefabcd", tx.to)
        assertEquals("123", tx.valueWei)
        assertEquals("1676486955", tx.gasPriceWei)
        assertEquals("832055", tx.gasLimit)
        assertEquals(42L, tx.nonce)
        assertEquals("0x2213bc0b", tx.dataHex)
    }

    @Test
    fun approvalTransactionUsesQuoteGasPriceAndAllowanceHolderSpender() {
        val tx = approvalTransactionFor(
            chain = SupportedChain.Base,
            sellAddress = "0xA0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            spender = "0x0000000000001ff3684f28c67538d4d072c22734",
            nonce = 7,
            gasPriceWei = "123456789",
        )

        assertEquals(8453L, tx.chainId)
        assertEquals("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", tx.to)
        assertEquals("0", tx.valueWei)
        assertEquals("123456789", tx.gasPriceWei)
        assertEquals("80000", tx.gasLimit)
        assertEquals(7L, tx.nonce)
        assertEquals(
            "0x095ea7b3" +
                "0000000000000000000000000000000000001ff3684f28c67538d4d072c22734" +
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            tx.dataHex,
        )
    }
}
