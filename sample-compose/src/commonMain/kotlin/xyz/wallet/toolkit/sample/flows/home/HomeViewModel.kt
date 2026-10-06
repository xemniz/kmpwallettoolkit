package xyz.wallet.toolkit.sample.flows.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.sample.portfolio.PortfolioRepository
import xyz.wallet.toolkit.sample.portfolio.PortfolioState

class HomeViewModel(
    private val repo: PortfolioRepository,
) : ViewModel() {

    private val _selectedChainId = MutableStateFlow(SupportedChain.Ethereum.id)
    val selectedChainId: StateFlow<Long> = _selectedChainId.asStateFlow()

    val ui: StateFlow<HomeUiState> = combine(repo.state, _selectedChainId) { portfolios, selected ->
        HomeUiState(
            portfolios = portfolios,
            selectedChainId = selected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(
            portfolios = repo.state.value,
            selectedChainId = _selectedChainId.value,
        ),
    )

    fun selectChain(chainId: Long) {
        _selectedChainId.value = chainId
    }

    fun ensureLoaded(wallet: Wallet, chains: List<SupportedChain>) {
        chains.forEach { chain ->
            repo.ensureLoaded(wallet.address(chain).lowercase(), chain)
        }
    }

    fun refresh(wallet: Wallet, chain: SupportedChain) {
        repo.refresh(wallet.address(chain).lowercase(), chain)
    }

    fun clearCache() {
        repo.clear()
    }
}

data class HomeUiState(
    val portfolios: Map<Long, PortfolioState>,
    val selectedChainId: Long,
) {
    fun portfolioFor(chainId: Long): PortfolioState =
        portfolios[chainId] ?: PortfolioState.Loading
}
