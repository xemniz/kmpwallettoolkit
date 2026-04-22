package xyz.wallet.toolkit.sample.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.wallet.toolkit.sample.WalletSampleApp
import xyz.wallet.toolkit.sample.state.SecureWalletStorageRuntime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        SecureWalletStorageRuntime.install(AndroidSecureWalletStorage(applicationContext))
        super.onCreate(savedInstanceState)
        setContent {
            WalletSampleApp()
        }
    }
}

