package xyz.wallet.toolkit.sample.flows.send

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText

private const val HOLD_MS: Long = 1_500L
private val WEI_PER_ETH: BigDecimal = BigDecimal.TEN.pow(18)

/**
 * Review sheet with hold-to-sign. No signing payload, no signed hex, no
 * address-derivative string is emitted through logs or exception messages
 * anywhere in this composable — the display strings below are the user's
 * own inputs (recipient / amount / gas) and the truncated `fromAddress`
 * (CLAUDE.md §4.1: not key material, but kept out of logs regardless).
 *
 * `onSign` is a `suspend () -> Unit` that runs the assembler. The sheet
 * only invokes it after a sustained 1.5s press; releasing early cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewAndSignSheet(
    state: SendState,
    chain: SupportedChain,
    fromAddress: String,
    onDismiss: () -> Unit,
    onSign: suspend () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WalletColors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Review",
                color = WalletColors.textPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            ReviewRow("From", truncateAddress(fromAddress))
            ReviewRow("To", truncateAddress(state.recipientNormalized ?: state.recipientRaw))
            ReviewRow("Amount", "${state.amountEth} ETH")
            ReviewRow("Max fee", "${state.maxFeeGwei} gwei")
            ReviewRow("Priority", "${state.maxPriorityGwei} gwei")
            ReviewRow("Network", chain.displayName)
            ReviewRow("Est. max fee", estMaxFeeEth(state.maxFeeGwei) + " ETH")

            when (val s = state.submission) {
                is SubmissionStatus.Error -> Text(
                    text = s.userMessage,
                    color = WalletColors.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                else -> { /* Idle / Submitting have no inline text here */ }
            }

            HoldToSignButton(
                enabled = state.submission != SubmissionStatus.Submitting,
                onSign = onSign,
            )
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = WalletColors.textSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        MonoText(text = value)
    }
}

@Composable
private fun HoldToSignButton(
    enabled: Boolean,
    onSign: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var signing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        if (signing) return@detectTapGestures
                        holdJob?.cancel()
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(durationMillis = HOLD_MS.toInt()))
                        }
                        holdJob = job
                        val fired = scope.launch {
                            delay(HOLD_MS)
                            if (!signing) {
                                signing = true
                                onSign()
                                signing = false
                            }
                        }
                        val released = tryAwaitRelease()
                        if (!released || progress.value < 1f) {
                            fired.cancel()
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(150)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(
                color = WalletColors.outline.copy(alpha = 0.4f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
            drawArc(
                color = WalletColors.accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
        }
        Text(
            text = if (signing) "Signing…" else "Hold to sign",
            color = WalletColors.textPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
    }

    LaunchedEffect(enabled) {
        if (!enabled) progress.snapTo(0f)
    }
}

internal fun truncateAddress(addr: String): String {
    if (addr.length <= 12) return addr
    return addr.take(6) + "…" + addr.takeLast(4)
}

/**
 * Pure presentation: estimates `21000 * maxFeePerGas` in ETH. Never fed back
 * into signing. Returns "—" on malformed input rather than throwing.
 */
internal fun estMaxFeeEth(maxFeeGwei: String): String {
    return try {
        val gwei = BigDecimal(maxFeeGwei.trim())
        val feeWei = gwei.multiply(BigDecimal(BigInteger.TEN.pow(9)))
            .multiply(BigDecimal("21000"))
        feeWei.divide(WEI_PER_ETH, 8, RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString()
    } catch (_: NumberFormatException) {
        "—"
    } catch (_: ArithmeticException) {
        "—"
    }
}
