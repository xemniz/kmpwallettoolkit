package xyz.wallet.toolkit.sample.nav

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import xyz.wallet.toolkit.sample.WelcomeScreen
import xyz.wallet.toolkit.sample.flows.create.CreateWalletScreen
import xyz.wallet.toolkit.sample.flows.home.HomeScreen
import xyz.wallet.toolkit.sample.flows.import.ImportWalletScreen
import xyz.wallet.toolkit.sample.flows.send.SendScreen
import xyz.wallet.toolkit.sample.flows.swap.SwapScreen
import xyz.wallet.toolkit.sample.flows.tx.TxStatusScreen

@Composable
actual fun WalletNavHost(
    hydrating: Boolean,
    backStack: NavBackStack<Route>,
    navigator: Navigator,
) {
    if (hydrating) {
        return
    }

    when (val route = navigator.current) {
        is Route.Welcome -> WelcomeScreen(navigator)
        is Route.Create -> CreateWalletScreen(navigator)
        is Route.Import -> ImportWalletScreen(navigator)
        is Route.Home -> HomeScreen(navigator)
        is Route.Send -> SendScreen(route, navigator)
        is Route.Swap -> SwapScreen(route, navigator)
        is Route.TxStatus -> TxStatusScreen(route, navigator)
    }
}

