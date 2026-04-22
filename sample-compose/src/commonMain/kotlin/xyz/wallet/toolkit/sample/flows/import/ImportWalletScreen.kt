package xyz.wallet.toolkit.sample.flows.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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

private const val GENERIC_RESTORE_ERROR =
    "Could not restore wallet — check the phrase and try again."

/**
 * Classic-A mnemonic-import screen.
 *
 * CLAUDE.md §4.1: the pasted phrase is never logged, never interpolated into
 * an exception message, and never echoed in the error surface. On failure we
 * show a fixed generic string and silently drop the caught Throwable.
 */
@Composable
fun ImportWalletScreen(navigator: Navigator) {
    val state = rememberImportWalletState()
    val session = LocalWalletSession.current

    PhoneFrame {
        BackBar(onBack = { navigator.pop() }, title = "Restore wallet")

        SourcePickerTabs()

        val validation = state.validation
        val wordCountLabel = when (validation) {
            ValidationResult.Empty -> ""
            is ValidationResult.WrongWordCount -> "${validation.count} words"
            is ValidationResult.Valid -> "${validation.words.size} words"
        }

        MnemonicPasteField(
            value = state.input,
            onValueChange = state::onInputChange,
            wordCountLabel = wordCountLabel,
        )

        PrimaryButton(
            text = "Restore",
            onClick = {
                // Guard also enforced by `enabled`, but defense-in-depth:
                // never read `input` unless validation has already normalized it.
                val current = state.validation
                if (current is ValidationResult.Valid && !state.isSubmitting) {
                    state.isSubmitting = true
                    val phrase = current.words.joinToString(" ")
                    try {
                        val wallet = Wallet.fromMnemonicWithTrustWalletCore(phrase)
                        // Force a derivation so an invalid-but-correct-word-count
                        // phrase surfaces here rather than on the Home screen.
                        wallet.address(session.selectedChain)
                        runCatching { SecureWalletStorageRuntime.get().save(phrase) }
                        session.wallet = wallet
                        navigator.replace(Route.Home)
                    } catch (t: Throwable) {
                        // CLAUDE.md §4.1: do NOT reference t.message / t.cause —
                        // the toolkit may include the offending input in either.
                        state.markSubmissionError()
                        state.isSubmitting = false
                    }
                }
            },
            enabled = validation is ValidationResult.Valid && !state.isSubmitting,
        )

        if (state.submissionError) {
            Text(
                text = GENERIC_RESTORE_ERROR,
                color = WalletColors.accent,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SourcePickerTabs() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceTab(label = "Phrase", selected = true, enabled = true)
        SourceTab(label = "Private key", selected = false, enabled = false)
        SourceTab(label = "Keystore", selected = false, enabled = false)
        SourceTab(label = "Watch-only", selected = false, enabled = false)
    }
}

@Composable
private fun SourceTab(label: String, selected: Boolean, enabled: Boolean) {
    FilterChip(
        selected = selected,
        onClick = { /* no-op: Phrase is the only wired path; others are Coming soon */ },
        enabled = enabled,
        label = {
            if (enabled) {
                Text(text = label)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = label)
                    Text(
                        text = "Coming soon",
                        style = MaterialTheme.typography.labelMedium,
                        color = WalletColors.textSecondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = WalletColors.surface,
            labelColor = WalletColors.textPrimary,
            selectedContainerColor = WalletColors.accent,
            selectedLabelColor = Color.White,
        ),
    )
}
