package xyz.wallet.toolkit.sample.flows.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.core.ChainRegistry
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.rpc.RpcClientFactory
import xyz.wallet.toolkit.sample.state.LocalWalletSession
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.BackBar
import xyz.wallet.toolkit.sample.ui.ChainChip
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

/**
 * Send flow host. Reads the active wallet via [LocalWalletSession]. The chain
 * is pinned from the [Route.Send] parameter and NOT editable here (spec: no
 * chain switching inside Send).
 *
 * Review CTA is enabled only when all four user inputs pass inline validation
 * ([canReview]); hitting it opens the [ReviewAndSignSheet] which owns the
 * hold-to-sign gesture and invokes the supplied [SendTxAssembler].
 */
@Composable
fun SendScreen(route: Route.Send, navigator: Navigator) {
    val session = LocalWalletSession.current
    val wallet = session.wallet
    val chain = ChainRegistry.byId(route.chainId)
    val state = remember(route.chainId) { SendState(chainId = route.chainId) }
    var showReview by remember { mutableStateOf(false) }

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

        // Chain chip is read-only in this flow. We still render it so the
        // user sees the network they're committing against. Tap is a no-op.
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

        Spacer(Modifier.height(0.dp).weight(1f))

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
                onClick = { showReview = true },
            )
        }
    }

    if (showReview && wallet != null && chain != null) {
        val assembler = remember(wallet, chain, navigator) {
            SendTxAssembler(
                wallet = wallet,
                rpc = RpcClientFactory.forChain(chain),
                chain = chain,
                navigator = navigator,
            )
        }
        ReviewAndSignSheet(
            state = state,
            chain = chain,
            fromAddress = wallet.address(chain).lowercase(),
            onDismiss = { showReview = false },
            onSign = {
                state.submission = SubmissionStatus.Submitting
                val result = assembler.assembleAndBroadcast(state)
                when (result) {
                    is SendResult.Success -> {
                        // Navigator.replace already invoked inside the assembler.
                        // Clear local submission so that, if we ever re-enter,
                        // the state does not leak the prior run.
                        state.submission = SubmissionStatus.Idle
                        showReview = false
                    }
                    is SendResult.Failure -> {
                        state.submission = SubmissionStatus.Error(result.userMessage)
                    }
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

