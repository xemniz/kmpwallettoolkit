package xyz.wallet.toolkit.sample.portfolio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.format.EthFormat
import xyz.wallet.toolkit.sample.rpc.RpcClientFactory

/**
 * App-scoped portfolio cache. Registered as a Koin single so every ViewModel
 * that needs it shares the same in-memory state — re-entering Home after a
 * Send no longer triggers a cold refetch.
 *
 * Fetches per (address, chainId) are idempotent: [ensureLoaded] is a no-op
 * if a successful or in-flight fetch already exists for that key.
 */
class PortfolioRepository(
    private val client: ZerionClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow<Map<Long, PortfolioState>>(emptyMap())
    val state: StateFlow<Map<Long, PortfolioState>> = _state.asStateFlow()

    private val jobs: MutableMap<Long, Job> = mutableMapOf()
    private var currentAddress: String? = null

    fun ensureLoaded(address: String, chain: SupportedChain) {
        if (currentAddress != address) {
            currentAddress = address
            clearInternal()
        }
        when (_state.value[chain.id]) {
            is PortfolioState.Value, PortfolioState.Loading -> return
            else -> refresh(address, chain)
        }
    }

    fun refresh(address: String, chain: SupportedChain) {
        jobs[chain.id]?.cancel()
        _state.update { it + (chain.id to PortfolioState.Loading) }
        jobs[chain.id] = scope.launch {
            val next = try {
                PortfolioState.Value(withNativeBalanceFallback(client.fetchPortfolio(address, chain), address, chain))
            } catch (t: Throwable) {
                PortfolioState.Error("Couldn't load portfolio")
            }
            _state.update { it + (chain.id to next) }
        }
    }

    fun clear() {
        currentAddress = null
        clearInternal()
    }

    private fun clearInternal() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _state.value = emptyMap()
    }

    private suspend fun withNativeBalanceFallback(
        snapshot: PortfolioSnapshot,
        address: String,
        chain: SupportedChain,
    ): PortfolioSnapshot {
        val nativeBalance = runCatching {
            EthFormat.weiHexToEthDecimal(
                RpcClientFactory.forChain(chain).getBalance(address, "latest"),
            )
        }.getOrNull()

        if (nativeBalance.isNullOrBlank() || nativeBalance == "—" || nativeBalance == "0") {
            return snapshot
        }

        val existingNative = snapshot.native
        val native = TokenPosition(
            symbol = chain.ticker,
            name = "${chain.displayName} ${chain.ticker}",
            quantityDecimal = nativeBalance,
            valueUsd = existingNative?.valueUsd,
            iconUrl = existingNative?.iconUrl,
            isNative = true,
        )

        val tokens = if (existingNative == null) {
            listOf(native) + snapshot.tokens
        } else {
            snapshot.tokens.map { token -> if (token.isNative) native else token }
        }

        return snapshot.copy(tokens = tokens)
    }
}
