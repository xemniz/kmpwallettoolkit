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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.portfolio.PortfolioState
import xyz.wallet.toolkit.sample.portfolio.TokenPosition
import xyz.wallet.toolkit.sample.portfolio.formatUsd
import xyz.wallet.toolkit.sample.portfolio.trimDecimal
import xyz.wallet.toolkit.sample.theme.WalletColors

/**
 * Assets list for the currently-selected chain. Three surfaces:
 * - Loading → three shimmer-ish placeholder rows
 * - Value   → one row per token (symbol, balance, USD)
 * - Error   → inline error with a Retry that refetches just this chain
 */
@Composable
fun PortfolioList(
    chain: SupportedChain,
    entry: PortfolioState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (entry) {
            is PortfolioState.Loading -> items(3) { PlaceholderRow() }
            is PortfolioState.Value -> {
                if (entry.snapshot.tokens.isEmpty()) {
                    item { EmptyRow(chain) }
                } else {
                    items(entry.snapshot.tokens) { token -> TokenRow(token) }
                }
            }
            is PortfolioState.Error -> item { ErrorRow(entry.message, onRetry) }
        }
    }
}

@Composable
private fun TokenRow(token: TokenPosition) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(WalletColors.surface)
                .border(1.dp, WalletColors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = token.symbol.take(1).uppercase(),
                color = WalletColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = token.symbol,
                color = WalletColors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${trimDecimal(token.quantityDecimal)} ${token.symbol}",
                color = WalletColors.textSecondary,
                fontSize = 11.sp,
            )
        }
        Text(
            text = token.valueUsd?.let { formatUsd(it) } ?: "—",
            color = WalletColors.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PlaceholderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = WalletColors.accent,
        )
        Text(text = "Loading…", color = WalletColors.textSecondary)
    }
}

@Composable
private fun EmptyRow(chain: SupportedChain) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WalletColors.surface)
            .padding(16.dp),
    ) {
        Text(
            text = "No assets on ${chain.displayName}",
            color = WalletColors.textSecondary,
        )
    }
}

@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetry)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = message, color = WalletColors.textSecondary)
        TextButton(onClick = onRetry) {
            Text(text = "Retry", color = WalletColors.accent)
        }
    }
}
