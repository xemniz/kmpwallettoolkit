package xyz.wallet.toolkit.sample.flows.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.rpc.RpcClientFactory
import xyz.wallet.toolkit.sample.state.WalletSession

/**
 * Handle for per-row retries. One instance per HomeScreen, captured via
 * [rememberHomeLoader]. `refetch(chain)` kicks off a fresh fetch for a single
 * chain without touching the other chains' entries in [HomeState.balances].
 *
 * Intentionally internal: the UI layer in this package is the only caller.
 */
class HomeLoader internal constructor(
    private val scope: CoroutineScope,
    private val session: WalletSession,
    private val state: HomeState,
) {
    fun refetch(chain: SupportedChain) {
        val wallet = session.wallet ?: return
        state.balances[chain.id] = ChainBalance.Loading
        scope.launch {
            runFetch(wallet, chain, state)
        }
    }
}

/**
 * Builds a [HomeLoader] and wires the one-shot fan-out fetch.
 *
 * Contract:
 *  - The [LaunchedEffect] is keyed on `session.wallet`. Any wallet swap
 *    (import, create, logout) re-runs the effect and re-fetches every chain.
 *  - Each chain gets its own `launch`. Per-chain `try/catch` over `Throwable`
 *    guarantees that one chain's failure never cancels siblings (CLAUDE.md §4
 *    failure-isolation expectation for this flow).
 *  - Addresses are lowercased at the RPC call site (CLAUDE.md §4.7).
 *  - No randomness is used (no retry backoff jitter, no request-id mint).
 */
@Composable
fun rememberHomeLoader(
    session: WalletSession,
    state: HomeState,
    chains: List<SupportedChain>,
): HomeLoader {
    val scope = rememberCoroutineScope()
    val loader = remember(session, state) { HomeLoader(scope, session, state) }
    val wallet = session.wallet
    LaunchedEffect(wallet) {
        if (wallet == null) return@LaunchedEffect
        chains.forEach { chain ->
            state.balances[chain.id] = ChainBalance.Loading
        }
        chains.forEach { chain ->
            launch { runFetch(wallet, chain, state) }
        }
    }
    return loader
}

/**
 * Single-chain fetch. Never rethrows — the per-row Error state is the channel
 * for reporting to the UI. The raw `Throwable.message` may carry RPC internals
 * so the UI surfaces "Unavailable" instead of `message` (see PortfolioList).
 */
private suspend fun runFetch(
    wallet: xyz.wallet.toolkit.core.Wallet,
    chain: SupportedChain,
    state: HomeState,
) {
    val addr = wallet.address(chain).lowercase()
    val result = try {
        val weiHex = RpcClientFactory.forChain(chain).getBalance(addr)
        ChainBalance.Value(weiHex)
    } catch (t: Throwable) {
        ChainBalance.Error(t.message ?: "rpc error")
    }
    state.balances[chain.id] = result
}
