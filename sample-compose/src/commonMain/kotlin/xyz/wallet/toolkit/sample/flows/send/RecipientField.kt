package xyz.wallet.toolkit.sample.flows.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.sample.platform.rememberClipboardTextHandler
import xyz.wallet.toolkit.sample.theme.WalletColors

/**
 * Recipient-address utilities and field.
 *
 * Validation rule (spec §Recipient validation + CLAUDE.md §4.7):
 *  - Regex `^0x[0-9a-fA-F]{40}$`. Accepts mixed case (EIP-55 checksummed
 *    addresses pass without verification — we intentionally do NOT verify
 *    the checksum in this spec; we only normalize).
 *  - `normalizeRecipient` lowercases for storage and all downstream
 *    comparisons. UI display may keep original casing.
 */
private val ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")

enum class ValidationResult { Valid, Invalid }

fun validateRecipient(raw: String): ValidationResult =
    if (ADDRESS_REGEX.matches(raw.trim())) ValidationResult.Valid else ValidationResult.Invalid

fun normalizeRecipient(raw: String): String = raw.trim().lowercase()

@Composable
fun RecipientField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = rememberClipboardTextHandler()
    val trimmed = value.trim()
    val isInvalid = trimmed.isNotEmpty() && validateRecipient(trimmed) == ValidationResult.Invalid

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Recipient") },
            placeholder = { Text("0x…") },
            singleLine = true,
            isError = isInvalid,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val pasted = clipboard.getText()
                    if (pasted != null) onValueChange(pasted)
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletColors.textPrimary),
            ) {
                Text("Paste")
            }
            OutlinedButton(
                onClick = { /* QR stub — camera integration out of scope */ },
                enabled = false,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletColors.textPrimary),
            ) {
                Text("QR")
            }
        }
        if (isInvalid) {
            Text(
                text = "Recipient address is not valid.",
                color = WalletColors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
