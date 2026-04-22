package xyz.wallet.toolkit.sample.flows.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.portfolio.PortfolioState

/**
 * In-memory Home-screen state. One [PortfolioState] per chain id plus the
 * currently-selected chain for the Send CTA. No persistence — the Home flow
 * re-fetches on every entry and on `session.wallet` change.
 *
 * `toString()` is deliberately bland: the wallet address is re-derived from
 * `session.wallet` at render time and per-chain portfolios have their own
 * redacted `toString()` (CLAUDE.md §4.1).
 */
class HomeState {
    val portfolios = mutableStateMapOf<Long, PortfolioState>()
    var selectedChainId: Long by mutableStateOf(SupportedChain.Ethereum.id)

    override fun toString(): String =
        "HomeState(selectedChainId=$selectedChainId, chains=${portfolios.keys})"
}
