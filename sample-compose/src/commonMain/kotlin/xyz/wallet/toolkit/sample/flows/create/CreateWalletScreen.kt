package xyz.wallet.toolkit.sample.flows.create

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.state.LocalWalletSession
import xyz.wallet.toolkit.sample.state.SecureWalletStorageRuntime
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.BackBar
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

private enum class Step { Intro, Reveal, Confirm }

/**
 * Top-level Create-wallet flow. Intro → Reveal → Confirm. On a correct
 * confirm pick, installs the generated wallet on `WalletSession` and routes
 * to `Route.Home` exactly once (the `LaunchedEffect` keys on `confirmed`).
 */
@Composable
fun CreateWalletScreen(navigator: Navigator) {
    val session = LocalWalletSession.current
    val state = remember { CreateWalletState() }
    var step by remember { mutableStateOf(Step.Intro) }

    LaunchedEffect(state.confirmed) {
        if (state.confirmed) {
            val created = state.wallet
            if (created != null) {
                runCatching { SecureWalletStorageRuntime.get().save(created.mnemonic) }
                session.wallet = created
                navigator.replace(Route.Home)
            }
        }
    }

    PhoneFrame {
        BackBar(
            onBack = {
                if (step == Step.Intro) navigator.pop() else step = Step.Intro
            },
            title = "Create wallet",
        )
        Crossfade(targetState = step) { current ->
            when (current) {
                Step.Intro -> IntroStep(
                    error = state.error,
                    onCreate = {
                        val result = runCatching { Wallet.createWithTrustWalletCore() }
                        val created = result.getOrNull()
                        if (created == null) {
                            // Intentionally drop throwable.message — may reference sensitive material (CLAUDE.md §4.1).
                            state.error = "Wallet creation failed"
                        } else {
                            val prepared = state.prepareConfirm(created.mnemonic)
                            if (!prepared) {
                                state.wallet = null
                                state.error = "Wallet creation failed"
                            } else {
                                state.wallet = created
                                state.error = null
                                state.revealed = false
                                step = Step.Reveal
                            }
                        }
                    },
                )
                Step.Reveal -> {
                    val wallet = state.wallet
                    if (wallet == null) {
                        // Defensive: should not happen — Intro only advances on success.
                        step = Step.Intro
                    } else {
                        RevealStep(
                            mnemonic = wallet.mnemonic,
                            revealed = state.revealed,
                            onReveal = { state.revealed = true },
                            onContinue = {
                                state.error = null
                                step = Step.Confirm
                            },
                        )
                    }
                }
                Step.Confirm -> ConfirmStep(
                    state = state,
                    onPick = { picked ->
                        val wallet = state.wallet
                        if (wallet == null) {
                            step = Step.Intro
                        } else {
                            val words = wallet.mnemonic.trim().split(Regex("\\s+"))
                            val correct = words.getOrNull(state.confirmIndex)
                            if (correct != null && picked == correct) {
                                state.error = null
                                state.confirmed = true
                            } else {
                                state.error = "Try again"
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun IntroStep(error: String?, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Create a new wallet", color = WalletColors.textPrimary)
        Text(
            text = "We will generate a 12-word recovery phrase. Write it down — it is the only way to restore this wallet.",
            color = WalletColors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = if (error == null) "Create new wallet" else "Retry",
            onClick = onCreate,
        )
        if (error != null) {
            Text(text = error, color = WalletColors.textSecondary)
        }
    }
}

@Composable
private fun RevealStep(
    mnemonic: String,
    revealed: Boolean,
    onReveal: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Your recovery phrase", color = WalletColors.textPrimary)
        Text(
            text = "Tap the card below to reveal your 12 words. Do not share them.",
            color = WalletColors.textSecondary,
        )
        MnemonicRevealCard(
            mnemonic = mnemonic,
            revealed = revealed,
            onReveal = onReveal,
        )
        PrimaryButton(
            text = "I wrote it down",
            onClick = onContinue,
            enabled = revealed,
        )
    }
}

@Composable
private fun ConfirmStep(state: CreateWalletState, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Confirm your phrase", color = WalletColors.textPrimary)
        ConfirmWordStep(
            targetIndexOneBased = state.confirmIndex + 1,
            options = state.confirmOptions,
            onPick = onPick,
            errorHint = state.error,
        )
    }
}
