package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tripwire for the Android `TrustWalletCoreNativeBridge.toCoinType()` `when` expression.
 *
 * The bridge lives in androidMain and cannot be invoked from commonTest, so this test
 * asserts at the SupportedChain level: every enum entry must appear in [expected]. When a
 * new chain is added, this test fails and the failure message points the implementer at
 * the bridge — preventing a recurrence of the spec E/F oversight that broke S1.
 */
class ChainCoinTypeMappingTest {

    // Source of truth for what the Android bridge's `when` must map each chain to.
    // Keep in sync with TrustWalletCoreNativeBridge.android.kt::toCoinType().
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
        assertEquals(6, SupportedChain.entries.size, "Spec D was written for 6 chains; update this test.")
        assertEquals(6, expected.size)
    }
}
