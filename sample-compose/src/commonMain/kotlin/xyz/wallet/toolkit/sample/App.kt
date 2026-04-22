package xyz.wallet.toolkit.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.sample.flows.create.CreateWalletScreen
import xyz.wallet.toolkit.sample.flows.home.HomeScreen
import xyz.wallet.toolkit.sample.flows.import.ImportWalletScreen
import xyz.wallet.toolkit.sample.flows.send.SendScreen
import xyz.wallet.toolkit.sample.flows.tx.TxStatusScreen
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.nav.rememberNavigator
import xyz.wallet.toolkit.sample.state.SecureWalletStorageRuntime
import xyz.wallet.toolkit.sample.state.WalletSession
import xyz.wallet.toolkit.sample.state.WalletSessionHolder
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.theme.WalletTheme
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

@Composable
fun WalletSampleApp() {
    val navigator = rememberNavigator()
    val session = remember { WalletSession() }
    var hydrating by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val stored = runCatching { SecureWalletStorageRuntime.get().load() }.getOrNull()
        if (!stored.isNullOrEmpty()) {
            runCatching {
                session.wallet = Wallet.fromMnemonicWithTrustWalletCore(stored)
                navigator.replace(Route.Home)
            }
        }
        hydrating = false
    }

    WalletTheme {
        WalletSessionHolder.Provide(session) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WalletColors.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (hydrating) {
                    // Brief blank splash — avoids flashing Welcome before
                    // we know whether a stored wallet exists.
                } else when (val route = navigator.current) {
                    is Route.Welcome -> WelcomeScreen(navigator)
                    is Route.Create -> CreateWalletScreen(navigator)
                    is Route.Import -> ImportWalletScreen(navigator)
                    is Route.Home -> HomeScreen(navigator)
                    is Route.Send -> SendScreen(route, navigator)
                    is Route.TxStatus -> TxStatusScreen(route, navigator)
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(navigator: Navigator) {
    PhoneFrame {
        Text(
            text = "kmp-wallet-toolkit",
            color = WalletColors.textPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "a low-fi showcase",
            color = WalletColors.textSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = "Create Wallet",
            onClick = { navigator.push(Route.Create) },
        )
        OutlinedButton(
            onClick = { navigator.push(Route.Import) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletColors.textPrimary),
        ) {
            Text("Import existing")
        }
    }
}

