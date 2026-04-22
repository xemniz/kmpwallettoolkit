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
                PortfolioState.Value(client.fetchPortfolio(address, chain))
            } catch (t: Throwable) {
                // CLAUDE.md §4.1: never propagate t.message — Zerion echoes
                // the wallet address into error bodies.
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
}
