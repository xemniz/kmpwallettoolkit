package xyz.wallet.toolkit.sample.flows.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import xyz.wallet.toolkit.core.SupportedChain

/**
 * Per-chain balance state for the Home screen.
 *
 * `Value.weiHex` is rendered through [xyz.wallet.toolkit.sample.format.EthFormat]
 * — it is not secret material, but we still avoid logging it so `toString()` on
 * [HomeState] redacts per-chain balances (CLAUDE.md §4.1).
 */
sealed interface ChainBalance {
    object Loading : ChainBalance
    data class Value(val weiHex: String) : ChainBalance {
        override fun toString(): String = "Value(redacted)"
    }
    data class Error(val message: String) : ChainBalance {
        override fun toString(): String = "Error(redacted)"
    }
}

/**
 * In-memory Home-screen state. Holds one [ChainBalance] per chain id plus the
 * currently-selected chain for the Send CTA. No persistence — the Home flow
 * re-fetches on every entry and on `session.wallet` change.
 *
 * `toString()` is deliberately bland: no address appears in this state (the
 * address is re-derived from `session.wallet` at render time) and balances
 * have their own redacted `toString()`.
 */
class HomeState {
    val balances = mutableStateMapOf<Long, ChainBalance>()
    var selectedChainId: Long by mutableStateOf(SupportedChain.Ethereum.id)

    override fun toString(): String =
        "HomeState(selectedChainId=$selectedChainId, chains=${balances.keys})"
}
