package xyz.wallet.toolkit.sample.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(extra: KoinAppDeclaration = {}) {
    startKoin {
        extra()
        modules(appModule)
    }
}

fun doInitKoinIos() = initKoin()
