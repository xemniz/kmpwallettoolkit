package xyz.wallet.toolkit.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.nav.WalletNavHost
import xyz.wallet.toolkit.sample.nav.rememberRouteBackStack
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
    val backStack = rememberRouteBackStack(Route.Welcome)
    val navigator = remember(backStack) { Navigator(backStack) }
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
            Surface(
                modifier = Modifier
                    .fillMaxSize(),
                color = WalletColors.background,
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val contentMaxWidth = if (maxWidth >= 840.dp) 560.dp else maxWidth
                    Box(
                        modifier = Modifier
                            .widthIn(max = contentMaxWidth)
                            .fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        WalletNavHost(
                            hydrating = hydrating,
                            backStack = backStack,
                            navigator = navigator,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(navigator: Navigator) {
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
