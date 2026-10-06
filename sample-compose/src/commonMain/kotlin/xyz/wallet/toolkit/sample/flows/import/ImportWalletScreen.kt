package xyz.wallet.toolkit.sample.flows.import

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route
import xyz.wallet.toolkit.sample.state.LocalWalletSession
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.BackBar
import xyz.wallet.toolkit.sample.ui.PhoneFrame
import xyz.wallet.toolkit.sample.ui.PrimaryButton

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
                if (state.submit(session)) navigator.replace(Route.Home)
            },
            enabled = validation is ValidationResult.Valid && !state.isSubmitting,
        )

        state.errorMessage?.let { error ->
            Text(
                text = error,
                color = WalletColors.accent,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SourcePickerTabs() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceTab(label = "Phrase", selected = true, enabled = true)
            SourceTab(label = "Private key", selected = false, enabled = false)
            SourceTab(label = "Keystore", selected = false, enabled = false)
            SourceTab(label = "Watch-only", selected = false, enabled = false)
        }
        Text(
            text = "Private key, keystore, and watch-only import are coming soon.",
            color = WalletColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SourceTab(label: String, selected: Boolean, enabled: Boolean) {
    FilterChip(
        selected = selected,
        onClick = { /* no-op: Phrase is the only wired path; others are Coming soon */ },
        enabled = enabled,
        label = {
            Text(text = label, maxLines = 1)
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = WalletColors.surface,
            labelColor = WalletColors.textPrimary,
            selectedContainerColor = WalletColors.accent,
            selectedLabelColor = Color.White,
        ),
    )
}
