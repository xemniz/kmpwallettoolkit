package xyz.wallet.toolkit.sample.di

import org.koin.dsl.module
import xyz.wallet.toolkit.sample.flows.home.HomeViewModel
import xyz.wallet.toolkit.sample.portfolio.PortfolioRepository
import xyz.wallet.toolkit.sample.portfolio.ZerionClient

/**
 * The showcase's single Koin module. Kept deliberately small: a REST client,
 * the shared portfolio cache, and the Home VM. Every other screen holds its
 * state in local Compose state for now — when another screen grows a proper
 * data-loading story, it joins this module.
 */
val appModule = module {
    single { ZerionClient() }
    single { PortfolioRepository(client = get()) }
    single { HomeViewModel(get()) }
}
