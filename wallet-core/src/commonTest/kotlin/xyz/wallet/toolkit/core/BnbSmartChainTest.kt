package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BnbSmartChainTest {
    @Test
    fun bnbSmartChainHasCorrectFields() {
        val bnb = SupportedChain.BnbSmartChain
        assertEquals(56L, bnb.id)
        assertEquals("BNB Smart Chain", bnb.displayName)
        assertEquals("BNB", bnb.ticker)
    }

    @Test
    fun registryResolvesBnbSmartChainById() {
        assertEquals(SupportedChain.BnbSmartChain, ChainRegistry.byId(56))
    }

    @Test
    fun registryAllContainsBnbSmartChain() {
        assertTrue(
            SupportedChain.BnbSmartChain in ChainRegistry.all(),
            "ChainRegistry.all() must contain BnbSmartChain",
        )
    }

    @Test
    fun registryAllMatchesEnumEntriesCount() {
        assertTrue(
            ChainRegistry.all().size >= 6,
            "expected at least six registered chains, got ${ChainRegistry.all().size}",
        )
        assertEquals(SupportedChain.entries.size, ChainRegistry.all().size)
    }
}
