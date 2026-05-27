package xyz.wallet.toolkit.sample.nav

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack

@Composable
expect fun WalletNavHost(
    hydrating: Boolean,
    backStack: NavBackStack<Route>,
    navigator: Navigator,
)

