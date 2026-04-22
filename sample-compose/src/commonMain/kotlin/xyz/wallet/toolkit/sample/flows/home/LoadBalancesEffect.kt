package xyz.wallet.toolkit.sample.flows.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.portfolio.PortfolioState
import xyz.wallet.toolkit.sample.portfolio.ZerionClient
import xyz.wallet.toolkit.sample.state.WalletSession

/**
 * Per-chain portfolio loader. One instance per HomeScreen; exposes
 * `refetch(chain)` for tap-to-retry. Each chain is fetched independently so
 * one chain's failure never cancels siblings.
 */
class HomeLoader internal constructor(
    private val scope: CoroutineScope,
    private val session: WalletSession,
    private val state: HomeState,
    private val client: ZerionClient,
) {
    fun refetch(chain: SupportedChain) {
        val wallet = session.wallet ?: return
        state.portfolios[chain.id] = PortfolioState.Loading
        scope.launch { runFetch(wallet, chain, state, client) }
    }
}

@Composable
fun rememberHomeLoader(
    session: WalletSession,
    state: HomeState,
    chains: List<SupportedChain>,
): HomeLoader {
    val scope = rememberCoroutineScope()
    val client = remember { ZerionClient() }
    val loader = remember(session, state) { HomeLoader(scope, session, state, client) }
    val wallet = session.wallet
    LaunchedEffect(wallet) {
        if (wallet == null) return@LaunchedEffect
        chains.forEach { chain ->
            state.portfolios[chain.id] = PortfolioState.Loading
        }
        chains.forEach { chain ->
            launch { runFetch(wallet, chain, state, client) }
        }
    }
    return loader
}

private suspend fun runFetch(
    wallet: xyz.wallet.toolkit.core.Wallet,
    chain: SupportedChain,
    state: HomeState,
    client: ZerionClient,
) {
    val address = wallet.address(chain).lowercase()
    state.portfolios[chain.id] = try {
        PortfolioState.Value(client.fetchPortfolio(address, chain))
    } catch (t: Throwable) {
        // CLAUDE.md §4.1: do not propagate t.message — Zerion echoes the
        // wallet address and request params into error bodies.
        PortfolioState.Error("Couldn't load portfolio")
    }
}
