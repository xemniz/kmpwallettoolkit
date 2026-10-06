package xyz.wallet.toolkit.sample.flows.tx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.core.ChainRegistry
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.rpc.RpcClientFactory
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

@Composable
fun TxStatusScreen(route: Route.TxStatus, navigator: Navigator) {
    PhoneFrame {
        val chain = ChainRegistry.byId(route.chainId)
        if (chain == null) {
            UnknownChainError(route.chainId, navigator)
            return@PhoneFrame
        }
        val rpc = remember(chain) { RpcClientFactory.forChain(chain) }
        var state by remember { mutableStateOf<TxStatusState>(TxStatusState.Pending(0, false)) }

        TxPollingEffect(txHash = route.txHash, rpc = rpc, onState = { state = it })

        when (val s = state) {
            is TxStatusState.Pending -> PendingState(s, route.txHash, chain)
            is TxStatusState.Confirmed -> ConfirmedState(s.receipt, navigator)
            is TxStatusState.Failed -> FailedState(s.reason, navigator)
        }
    }
}

@Composable
private fun FailedState(reason: String, navigator: Navigator) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Failed",
            color = WalletColors.textPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        Text(
            text = reason,
            color = WalletColors.textSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        PrimaryButton(
            text = "Back to Home",
            onClick = { navigator.replace(Route.Home) },
        )
    }
}

@Composable
private fun UnknownChainError(chainId: Long, navigator: Navigator) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Unsupported chain",
            color = WalletColors.textPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Chain id $chainId is not registered.",
            color = WalletColors.textSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        PrimaryButton(
            text = "Back to Home",
            onClick = { navigator.replace(Route.Home) },
        )
    }
}
