package xyz.wallet.toolkit.sample.flows.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.state.LocalWalletSession
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

/**
 * Top-level Create-wallet flow — Phase 1 shell. Intro step only; reveal and
 * confirm steps land in Phase 2.
 */
@Composable
fun CreateWalletScreen(navigator: Navigator) {
    // Reads LocalWalletSession now so Phase 2's LaunchedEffect can install the wallet.
    LocalWalletSession.current
    val state = remember { CreateWalletState() }
    PhoneFrame {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Create a new wallet", color = WalletColors.textPrimary)
            Text(
                text = "We will generate a 12-word recovery phrase. Write it down — it is the only way to restore this wallet.",
                color = WalletColors.textSecondary,
            )
            PrimaryButton(
                text = "Create new wallet",
                onClick = { /* Phase 2: invoke Wallet.createWithTrustWalletCore() */ },
            )
            val err = state.error
            if (err != null) {
                Text(text = err, color = WalletColors.textSecondary)
            }
        }
    }
}
