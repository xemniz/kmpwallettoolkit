package xyz.wallet.toolkit.sample.flows.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.core.ChainRegistry
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.execution.ExecutionReview
import xyz.wallet.toolkit.sample.execution.TransactionExecutionRepository
import org.koin.compose.koinInject
import xyz.wallet.toolkit.sample.state.LocalWalletSession
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.BackBar
import xyz.wallet.toolkit.sample.ui.ChainChip
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

@Composable
fun SendScreen(route: Route.Send, navigator: Navigator) {
    val session = LocalWalletSession.current
    val wallet = session.wallet
    val chain = ChainRegistry.byId(route.chainId)
    val state = remember(route.chainId) { SendState(chainId = route.chainId) }
    val execution: TransactionExecutionRepository = koinInject()
    val focusManager = LocalFocusManager.current
    var review by remember(wallet, route.chainId) { mutableStateOf<ExecutionReview?>(null) }
    var reviewError by remember { mutableStateOf<String?>(null) }

    PhoneFrame {
        BackBar(onBack = { navigator.pop() }, title = "Send")

        if (wallet == null || chain == null) {
            Text(
                text = if (wallet == null) "No wallet" else "Unsupported chain",
                color = WalletColors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            return@PhoneFrame
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChainChip(selected = chain, onSelect = { /* read-only */ })

            RecipientField(
                value = state.recipientRaw,
                onValueChange = { raw ->
                    state.recipientRaw = raw
                    state.recipientNormalized =
                        if (validateRecipient(raw) == ValidationResult.Valid) normalizeRecipient(raw) else null
                },
            )

            AmountField(
                value = state.amountEth,
                onValueChange = { state.amountEth = it },
            )

            GasFields(
                maxFee = state.maxFeeGwei,
                priority = state.maxPriorityGwei,
                onMaxFeeChange = { state.maxFeeGwei = it },
                onPriorityChange = { state.maxPriorityGwei = it },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (state.submission as? SubmissionStatus.Error)?.let { err ->
                Text(
                    text = err.userMessage,
                    color = WalletColors.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            PrimaryButton(
                text = "Review",
                enabled = canReview(state),
                onClick = {
                    try {
                        reviewError = null
                        review = execution.prepareSend(chain, wallet.address(chain).lowercase(), state)
                        focusManager.clearFocus()
                    } catch (_: Exception) {
                        state.submission = SubmissionStatus.Error("Could not prepare review. Check pending operations and try again.")
                    }
                },
            )
        }
    }

    review?.let { prepared ->
        ReviewAndSignSheet(
            review = prepared,
            error = reviewError,
            onDismiss = { review = null },
            onConfirm = {
                try {
                    val id = execution.start(prepared)
                    review = null
                    navigator.replace(Route.Operation(id))
                } catch (_: Exception) {
                    reviewError = "Could not start. Close the review and try again."
                }
            },
        )
    }
}

/**
 * Pure predicate — safe to call from composition. Does NOT throw on
 * malformed input; reuses the same parsers the inline validators use.
 */
fun canReview(state: SendState): Boolean {
    if (validateRecipient(state.recipientRaw) != ValidationResult.Valid) return false
    if (!parsesAsPositiveEth(state.amountEth)) return false
    if (!parsesAsGwei(state.maxFeeGwei)) return false
    if (!parsesAsGwei(state.maxPriorityGwei)) return false
    if (!priorityLeMaxFee(state.maxPriorityGwei, state.maxFeeGwei)) return false
    return true
}
