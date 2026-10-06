package xyz.wallet.toolkit.sample.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import xyz.wallet.toolkit.sample.di.initKoin
import xyz.wallet.toolkit.sample.state.SecureWalletStorageRuntime

class WalletSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SecureWalletStorageRuntime.install(AndroidSecureWalletStorage(this))
        initKoin { androidContext(this@WalletSampleApplication) }
    }
}
