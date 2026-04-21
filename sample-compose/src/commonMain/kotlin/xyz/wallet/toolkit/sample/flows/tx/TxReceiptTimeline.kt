package xyz.wallet.toolkit.sample.flows.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.sample.theme.WalletColors

/**
 * Three-step vertical timeline: Submitted -> Pending -> Included in block N.
 * Used by both Pending and Confirmed screens so the layout stays stable as
 * the state transitions.
 */
@Composable
fun TxReceiptTimeline(
    state: TxStatusState,
    modifier: Modifier = Modifier,
) {
    val isConfirmed = state is TxStatusState.Confirmed
    val blockNumberText: String = when (state) {
        is TxStatusState.Confirmed -> {
            val decoded = runCatching {
                state.receipt.blockNumber.removePrefix("0x").toLong(16)
            }.getOrNull()
            if (decoded != null) "Included in block #$decoded" else "Included in block"
        }
        else -> "Included in block"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        TimelineRow(
            label = "Submitted",
            filled = true,
            isCheck = true,
            showConnectorBelow = true,
        )
        TimelineRow(
            label = "Pending",
            filled = true,
            isCheck = isConfirmed,
            showConnectorBelow = true,
            dim = isConfirmed,
        )
        TimelineRow(
            label = blockNumberText,
            filled = isConfirmed,
            isCheck = isConfirmed,
            showConnectorBelow = false,
        )
    }
}

@Composable
private fun TimelineRow(
    label: String,
    filled: Boolean,
    isCheck: Boolean,
    showConnectorBelow: Boolean,
    dim: Boolean = false,
) {
    val dotColor = if (filled) WalletColors.accent else WalletColors.outline.copy(alpha = 0.3f)
    val textColor = if (filled) WalletColors.textPrimary else WalletColors.textSecondary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor),
                contentAlignment = Alignment.Center,
            ) {
                if (isCheck && filled) {
                    // Simple static check glyph: a small white dot on the filled circle.
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
            if (showConnectorBelow) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(WalletColors.outline.copy(alpha = 0.3f)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = if (dim) WalletColors.textSecondary else textColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
    }
}
