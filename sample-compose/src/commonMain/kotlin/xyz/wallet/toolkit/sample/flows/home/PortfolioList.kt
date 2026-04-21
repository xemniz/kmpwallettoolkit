package xyz.wallet.toolkit.sample.flows.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.BalanceRow

/**
 * A small, inline per-chain label used only inside PortfolioList. Deliberately
 * NOT reusing S1's `ChainChip` because that primitive is a multi-chain filter
 * row (FilterChip list with a selected-state); here we need a single-row label
 * per card. Keeping S1's ChainChip untouched per spec.
 */
@Composable
private fun ChainBadge(chain: SupportedChain) {
    Text(
        text = chain.displayName,
        color = WalletColors.textPrimary,
    )
}

/**
 * Vertical list of per-chain cards. Each card:
 *  - Shows Loading / Value / Error state for one chain.
 *  - Wraps S1's [BalanceRow] for the Value case only; BalanceRow does not
 *    model Loading/Error, so we render those states around it.
 *  - Clicking the card sets it as the Send target ([HomeState.selectedChainId]).
 *  - On Error, a "Retry" TextButton invokes [HomeLoader.refetch] for just
 *    that chain — sibling rows are untouched.
 *
 * Raw RPC error messages are never surfaced to the UI (they can carry
 * implementation-leaking text). The row shows "Unavailable" only.
 */
@Composable
fun PortfolioList(
    chains: List<SupportedChain>,
    state: HomeState,
    loader: HomeLoader,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        chains.forEach { chain ->
            val entry = state.balances[chain.id] ?: ChainBalance.Loading
            val selected = state.selectedChainId == chain.id
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(WalletColors.surface)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) WalletColors.accent else WalletColors.outline,
                        shape = shape,
                    )
                    .clickable { state.selectedChainId = chain.id }
                    .padding(16.dp),
            ) {
                when (entry) {
                    is ChainBalance.Loading -> LoadingRow(chain)
                    is ChainBalance.Value -> BalanceRow(chain = chain, weiHex = entry.weiHex)
                    is ChainBalance.Error -> ErrorRow(chain = chain, onRetry = { loader.refetch(chain) })
                }
            }
        }
    }
}

@Composable
private fun LoadingRow(chain: SupportedChain) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChainBadge(chain)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = WalletColors.accent,
            )
            Text(text = "Loading…", color = WalletColors.textSecondary)
        }
    }
}

@Composable
private fun ErrorRow(chain: SupportedChain, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChainBadge(chain)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Unavailable", color = WalletColors.textSecondary)
            TextButton(onClick = onRetry) {
                Text(text = "Retry", color = WalletColors.accent)
            }
        }
    }
}
