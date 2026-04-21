package xyz.wallet.toolkit.sample.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalWalletSession = staticCompositionLocalOf<WalletSession> {
    error("WalletSession not provided")
}

object WalletSessionHolder {
    @Composable
    fun Provide(session: WalletSession, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalWalletSession provides session, content = content)
    }
}
