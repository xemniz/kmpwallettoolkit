package xyz.wallet.toolkit.sample.flows.import

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import xyz.wallet.toolkit.sample.theme.MonoTextStyle
import xyz.wallet.toolkit.sample.theme.WalletColors

/**
 * Multi-line paste field for a BIP-39 mnemonic.
 *
 * CLAUDE.md §4.1: this composable must not log [value]. It also disables
 * soft-keyboard autocorrect / suggestions (ASCII + no capitalization) so the
 * IME does not capture words into a learned dictionary.
 */
@Composable
fun MnemonicPasteField(
    value: String,
    onValueChange: (String) -> Unit,
    wordCountLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            singleLine = false,
            textStyle = MonoTextStyle,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Ascii,
            ),
            placeholder = {
                Text(
                    text = "Paste your recovery phrase",
                    color = WalletColors.textSecondary,
                )
            },
        )
        if (wordCountLabel.isNotEmpty()) {
            Text(
                text = wordCountLabel,
                color = WalletColors.textSecondary,
                style = MonoTextStyle,
            )
        }
    }
}
