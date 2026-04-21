package xyz.wallet.toolkit.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.core.ChainRegistry
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.theme.WalletColors

private val S1_CHAINS = setOf(SupportedChain.Ethereum, SupportedChain.Base)

@Composable
fun ChainChip(
    selected: SupportedChain,
    onSelect: (SupportedChain) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chains = ChainRegistry.all().filter { it in S1_CHAINS }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chains.forEach { chain ->
            FilterChip(
                selected = chain == selected,
                onClick = { onSelect(chain) },
                label = { Text(chain.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = WalletColors.surface,
                    labelColor = WalletColors.textPrimary,
                    selectedContainerColor = WalletColors.accent,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}
