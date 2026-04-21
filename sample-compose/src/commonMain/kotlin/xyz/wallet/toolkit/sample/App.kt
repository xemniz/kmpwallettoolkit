package xyz.wallet.toolkit.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.nav.rememberNavigator
import xyz.wallet.toolkit.sample.state.WalletSession
import xyz.wallet.toolkit.sample.state.WalletSessionHolder
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.theme.WalletTheme
import xyz.wallet.toolkit.sample.ui.MonoText
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

@Composable
fun WalletSampleApp() {
    val navigator = rememberNavigator()
    val session = remember { WalletSession() }
    WalletTheme {
        WalletSessionHolder.Provide(session) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WalletColors.background)
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (val route = navigator.current) {
                    is Route.Welcome -> WelcomeScreen(navigator)
                    else -> PlaceholderScreen(route, navigator)
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

@Composable
private fun PlaceholderScreen(route: Route, navigator: Navigator) {
    PhoneFrame {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MonoText(text = "TODO: ${route::class.simpleName}")
            OutlinedButton(
                onClick = { navigator.pop() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletColors.textPrimary),
            ) {
                Text("Back")
            }
        }
    }
}
