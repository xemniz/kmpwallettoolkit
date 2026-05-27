package xyz.wallet.toolkit.sample.nav

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
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

    NavDisplay(
        backStack = backStack,
        onBack = { navigator.pop() },
        entryProvider = { route ->
            when (route) {
                is Route.Welcome -> NavEntry(route) { WelcomeScreen(navigator) }
                is Route.Create -> NavEntry(route) { CreateWalletScreen(navigator) }
                is Route.Import -> NavEntry(route) { ImportWalletScreen(navigator) }
                is Route.Home -> NavEntry(route) { HomeScreen(navigator) }
                is Route.Send -> NavEntry(route) { SendScreen(route, navigator) }
                is Route.Swap -> NavEntry(route) { SwapScreen(route, navigator) }
                is Route.TxStatus -> NavEntry(route) { TxStatusScreen(route, navigator) }
            }
        },
    )
}

