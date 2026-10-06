package xyz.wallet.toolkit.sample.flows.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.rpc.TransactionReceipt
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText
import xyz.wallet.toolkit.sample.ui.PrimaryButton

@Composable
fun ConfirmedState(
    receipt: TransactionReceipt,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(WalletColors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Text(
            text = "Confirmed",
            color = WalletColors.textPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        TxReceiptTimeline(TxStatusState.Confirmed(receipt))
        MonoText(
            text = shortenHash(receipt.transactionHash),
            color = WalletColors.textSecondary,
        )
        // Keep checksum casing out of address comparisons.
        val toText = receipt.to?.lowercase()
        if (toText != null) {
            MonoText(
                text = "to " + shortenHash(toText),
                color = WalletColors.textSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = "View on Home",
            onClick = { navigator.replace(Route.Home) },
        )
    }
}
