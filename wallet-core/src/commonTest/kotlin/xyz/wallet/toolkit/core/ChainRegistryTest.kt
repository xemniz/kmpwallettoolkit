package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChainRegistryTest {
    @Test
    fun registryResolvesOptimismByChainId() {
        val chain = ChainRegistry.byId(10)
        assertNotNull(chain, "ChainRegistry.byId(10) must resolve to a registered chain")
        assertEquals(SupportedChain.Optimism, chain)
    }

    @Test
    fun registryContainsOptimismAlongsideOriginalFour() {
        val all = ChainRegistry.all()
        // Additional supported chains do not invalidate the required-chain checks.
        assertTrue(
            all.size >= 5,
            "expected at least five registered chains, got ${all.size}",
        )
        assertTrue(
            SupportedChain.Ethereum in all,
            "Ethereum must remain registered",
        )
        assertTrue(
            SupportedChain.Base in all,
            "Base must remain registered",
        )
        assertTrue(
            SupportedChain.Polygon in all,
            "Polygon must remain registered",
        )
        assertTrue(
            SupportedChain.Arbitrum in all,
            "Arbitrum must remain registered",
        )
        assertTrue(
            SupportedChain.Optimism in all,
            "Optimism must be registered",
        )
    }

    @Test
    fun optimismConstantCarriesExpectedMetadata() {
        assertEquals(10L, SupportedChain.Optimism.id)
        assertEquals("Optimism", SupportedChain.Optimism.displayName)
        assertEquals("ETH", SupportedChain.Optimism.ticker)
    }

    @Test
    fun registryReturnsNullForUnknownChainId() {
        assertNull(ChainRegistry.byId(999_999L))
    }
}
