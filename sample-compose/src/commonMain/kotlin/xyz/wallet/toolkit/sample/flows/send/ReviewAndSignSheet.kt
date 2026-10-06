package xyz.wallet.toolkit.sample.flows.send

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.wallet.toolkit.sample.execution.ExecutionReview
import xyz.wallet.toolkit.sample.execution.ReviewedTransaction
import xyz.wallet.toolkit.sample.execution.StepKind
import xyz.wallet.toolkit.sample.flows.swap.rawToAmount
import xyz.wallet.toolkit.sample.format.EthFormat
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText
import xyz.wallet.toolkit.sample.ui.displayName

private const val HOLD_MS = 1_500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewAndSignSheet(
    review: ExecutionReview,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var consumed by remember(review) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = WalletColors.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Review", color = WalletColors.textPrimary)
            ReviewRow("Network", review.chain.displayName)
            ReviewRow("From", review.owner)
            val quote = review.swapQuote
            if (quote != null) {
                ReviewRow("Sell asset", quote.sell.address ?: "Native ${review.chain.ticker}")
                ReviewRow("You pay", "${rawToAmount(quote.sellAmountRaw, quote.sell.decimals, quote.sell.decimals)} ${quote.sell.symbol}")
                ReviewRow("Buy asset", quote.buy.address ?: "Native ${review.chain.ticker}")
                ReviewRow("You receive", "${rawToAmount(quote.buyAmountRaw, quote.buy.decimals, quote.buy.decimals)} ${quote.buy.symbol}")
                ReviewRow("Minimum received", "${rawToAmount(quote.minBuyAmountRaw, quote.buy.decimals, quote.buy.decimals)} ${quote.buy.symbol}")
                quote.allowanceTarget?.let { ReviewRow("Approval spender", it) }
                if (review.steps.none { it.kind == StepKind.Approve }) Text("No approval needed", color = WalletColors.textSecondary)
            }
            var totalFee = "0"
            review.steps.forEachIndexed { index, step ->
                Text("${index + 1}. ${step.kind.displayName()}", color = WalletColors.textPrimary)
                ReviewRow("To", step.transaction.to)
                ReviewRow("Network value", EthFormat.formatWeiAsEth(step.transaction.valueWei, 18) + " ${review.chain.ticker}")
                step.approvalAmountRaw?.let { amount ->
                    if (quote != null) ReviewRow("Allowance", "${rawToAmount(amount, quote.sell.decimals, quote.sell.decimals)} ${quote.sell.symbol}")
                }
                ReviewRow("Gas limit", step.transaction.gasLimit)
                ReviewRow("Maximum gas price", step.transaction.feePerGasWei + " wei")
                (step.transaction as? ReviewedTransaction.Type2)?.let { transaction ->
                    ReviewRow("Maximum priority fee", transaction.value.maxPriorityFeePerGasWei + " wei")
                }
                val fee = EthFormat.multiplyDecimalIntegers(step.transaction.gasLimit, step.transaction.feePerGasWei)
                totalFee = EthFormat.addWei(totalFee, fee)
                ReviewRow("Estimated max fee", EthFormat.formatWeiAsEth(fee, 18) + " ${review.chain.ticker}")
            }
            ReviewRow("Total estimated max fees", EthFormat.formatWeiAsEth(totalFee, 18) + " ${review.chain.ticker}")
            Text("This confirmation authorizes all ${review.steps.size} disclosed transactions. Changed swap terms require a new review.", color = WalletColors.textSecondary)
            error?.let { Text(it, color = WalletColors.textSecondary) }
            HoldToSignButton(enabled = !consumed) {
                consumed = true
                onConfirm()
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Column {
        Text(label, color = WalletColors.textSecondary)
        MonoText(value)
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
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(WalletColors.accent)
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
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val stroke = 3.dp.toPx()
                drawArc(
                    color = Color.White.copy(alpha = 0.3f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = Color.White,
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
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) progress.snapTo(0f)
    }
}
