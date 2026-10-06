package xyz.wallet.toolkit.sample.di

import org.koin.dsl.module
import xyz.wallet.toolkit.sample.flows.home.HomeViewModel
import xyz.wallet.toolkit.sample.flows.swap.SwapViewModelFactory
import xyz.wallet.toolkit.sample.flows.swap.TokenSearchClient
import xyz.wallet.toolkit.sample.flows.swap.ZeroExClient
import xyz.wallet.toolkit.sample.portfolio.PortfolioRepository
import xyz.wallet.toolkit.sample.portfolio.ZerionClient
import xyz.wallet.toolkit.sample.state.WalletSession

val appModule = module {
    single { WalletSession() }
    single { ZerionClient() }
    single { PortfolioRepository(client = get()) }
    single { HomeViewModel(get()) }
    single { ZeroExClient() }
    single { TokenSearchClient() }
    single { executionRepository(session = get(), quotes = get<ZeroExClient>()) }
    single { SwapViewModelFactory(zeroEx = get<ZeroExClient>(), search = get<TokenSearchClient>()) }
}
