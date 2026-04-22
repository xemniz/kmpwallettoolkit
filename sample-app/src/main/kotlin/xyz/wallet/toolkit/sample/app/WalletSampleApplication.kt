package xyz.wallet.toolkit.sample.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import xyz.wallet.toolkit.sample.di.initKoin

class WalletSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@WalletSampleApplication) }
    }
}
