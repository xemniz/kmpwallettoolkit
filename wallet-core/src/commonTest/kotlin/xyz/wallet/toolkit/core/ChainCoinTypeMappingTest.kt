package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checks that the expected CoinType inventory covers every supported chain. */
class ChainCoinTypeMappingTest {

    // This inventory does not execute or verify the native bridge mapping.
    private val expected: Map<SupportedChain, String> = mapOf(
        SupportedChain.Ethereum to "ETHEREUM",
        SupportedChain.Base to "ETHEREUM",
        SupportedChain.Arbitrum to "ETHEREUM",
        SupportedChain.Polygon to "POLYGON",
        SupportedChain.Optimism to "ETHEREUM",
        SupportedChain.BnbSmartChain to "SMARTCHAIN",
    )

    @Test
    fun every_supported_chain_has_an_expected_coin_type() {
        val uncovered = SupportedChain.entries.filter { it !in expected }
        assertTrue(
            uncovered.isEmpty(),
            "SupportedChain has entries with no CoinType mapping: $uncovered — " +
                "update both TrustWalletCoreNativeBridge.android.kt::toCoinType() and this test.",
        )
    }

    @Test
    fun expected_map_does_not_reference_removed_chains() {
        val known = SupportedChain.entries.toSet()
        val stale = expected.keys.filter { it !in known }
        assertTrue(
            stale.isEmpty(),
            "ChainCoinTypeMappingTest.expected references chains not in SupportedChain: $stale",
        )
    }

    @Test
    fun expected_map_covers_all_six_current_entries() {
        assertEquals(6, SupportedChain.entries.size, "Expected the six supported chains.")
        assertEquals(6, expected.size)
    }
}
