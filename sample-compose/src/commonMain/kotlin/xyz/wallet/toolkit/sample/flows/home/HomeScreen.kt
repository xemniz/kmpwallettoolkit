package xyz.wallet.toolkit.sample.flows.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.state.LocalWalletSession
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

/**
 * Chains rendered on the Home screen. SupportedChain includes Polygon /
 * Arbitrum / Optimism / BnbSmartChain but S4 is scoped to Ethereum + Base
 * only (see spec Non-goals).
 */
private val homeChains: List<SupportedChain> =
    listOf(SupportedChain.Ethereum, SupportedChain.Base)

/**
 * Portfolio entry point. Wires state, the fan-out fetch effect, the address
 * header, the per-chain cards, and the Send/Receive CTAs.
 *
 * Reads the active wallet via [LocalWalletSession]. The address displayed in
 * the header is lowercased (CLAUDE.md §4.7); the same lowercased form is also
 * what the RPC layer receives inside [rememberHomeLoader].
 *
 * Send CTA pushes `Route.Send(chainId = state.selectedChainId)`. It is disabled
 * while the selected chain's balance is still Loading to avoid initiating a
 * send flow on stale/unknown balance.
 *
 * Receive CTA is a stub — it opens a "Coming soon" AlertDialog. No navigation.
 */
@Composable
fun HomeScreen(navigator: Navigator) {
    val session = LocalWalletSession.current
    val wallet = session.wallet
    val state = remember { HomeState() }
    val loader = rememberHomeLoader(session, state, homeChains)
    var showReceive by remember { mutableStateOf(false) }

    PhoneFrame {
        if (wallet == null) {
            MonoText(text = "No wallet")
            OutlinedButton(
                onClick = { navigator.pop() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletColors.textPrimary),
            ) {
                Text("Back")
            }
            return@PhoneFrame
        }

        // The EVM address is the same across EVM chains; any chain works.
        // .lowercase() is applied inside AddressHeader as well.
        AddressHeader(address = wallet.address(SupportedChain.Ethereum).lowercase())

        PortfolioList(
            chains = homeChains,
            state = state,
            loader = loader,
        )

        Spacer(Modifier.height(0.dp).weight(1f))

        val selectedBalance = state.balances[state.selectedChainId]
        val sendEnabled = selectedBalance is ChainBalance.Value

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(
                text = "Send",
                enabled = sendEnabled,
                onClick = { navigator.push(Route.Send(chainId = state.selectedChainId)) },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { showReceive = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletColors.textPrimary),
            ) {
                Text("Receive")
            }
        }
    }

    if (showReceive) {
        AlertDialog(
            onDismissRequest = { showReceive = false },
            confirmButton = {
                TextButton(onClick = { showReceive = false }) { Text("OK") }
            },
            title = { Text("Receive") },
            text = { Text("Coming soon") },
        )
    }
}
