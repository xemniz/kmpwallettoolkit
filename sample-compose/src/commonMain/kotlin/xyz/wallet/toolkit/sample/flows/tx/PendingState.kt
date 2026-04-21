package xyz.wallet.toolkit.sample.flows.tx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText

@Composable
fun PendingState(
    state: TxStatusState.Pending,
    txHash: String,
    chain: SupportedChain,
    modifier: Modifier = Modifier,
) {
    var stubToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stubToast) {
        if (stubToast != null) {
            delay(2_000L)
            stubToast = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sending",
            color = WalletColors.textPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        AnimatedDots()
        Text(
            text = formatElapsed(state.elapsedSeconds),
            color = WalletColors.textSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        if (state.stalled) {
            Text(
                text = "Still waiting \u2014 the network may be congested.",
                color = WalletColors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
        }
        PendingChainBadge(chain)
        MonoText(
            text = shortenHash(txHash),
            color = WalletColors.textSecondary,
        )
        TxReceiptTimeline(state)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { stubToast = "Coming soon" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletColors.textPrimary),
            ) {
                Text("Speed up")
            }
            OutlinedButton(
                onClick = { stubToast = "Coming soon" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletColors.textPrimary),
            ) {
                Text("Cancel")
            }
        }
        AnimatedVisibility(visible = stubToast != null) {
            Text(
                text = stubToast ?: "",
                color = WalletColors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AnimatedDots() {
    val transition = rememberInfiniteTransition(label = "pending-dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pending-dots-phase",
    )
    val active = phase.toInt().coerceIn(0, 2)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { i ->
            val alpha = if (i == active) 1f else 0.3f
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(WalletColors.textSecondary.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun PendingChainBadge(chain: SupportedChain) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, WalletColors.outline.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = chain.displayName,
            color = WalletColors.textPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        )
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mm = safe / 60
    val ss = safe % 60
    val mmStr = if (mm < 10) "0$mm" else mm.toString()
    val ssStr = if (ss < 10) "0$ss" else ss.toString()
    return "$mmStr:$ssStr"
}

internal fun shortenHash(hash: String): String {
    if (hash.length <= 12) return hash
    val head = hash.take(6)
    val tail = hash.takeLast(4)
    return "$head\u2026$tail"
}
