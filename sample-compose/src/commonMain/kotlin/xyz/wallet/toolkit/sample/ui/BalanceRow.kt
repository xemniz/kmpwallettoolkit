package xyz.wallet.toolkit.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.format.EthFormat
import xyz.wallet.toolkit.sample.theme.WalletColors

@Composable
fun BalanceRow(
    chain: SupportedChain,
    weiHex: String?,
    modifier: Modifier = Modifier,
) {
    val formatted = if (weiHex == null) "—" else EthFormat.weiHexToEthDecimal(weiHex)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = chain.displayName, color = WalletColors.textPrimary)
        MonoText(text = "$formatted ${chain.ticker}")
    }
}
