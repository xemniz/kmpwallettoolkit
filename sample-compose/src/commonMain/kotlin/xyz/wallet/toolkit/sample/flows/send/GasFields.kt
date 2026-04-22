package xyz.wallet.toolkit.sample.flows.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.sample.format.EthFormat
import xyz.wallet.toolkit.sample.theme.WalletColors

/**
 * Two decimal inputs in gwei: `maxFeePerGas` and `maxPriorityFeePerGas`.
 * Inline error surfaces when priority > max fee; comparison is done as
 * `BigDecimal`, never as string.
 */
@Composable
fun GasFields(
    maxFee: String,
    priority: String,
    onMaxFeeChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxFeeInvalid = maxFee.trim().isNotEmpty() && !parsesAsGwei(maxFee)
    val priorityInvalid = priority.trim().isNotEmpty() && !parsesAsGwei(priority)
    val orderInvalid = !maxFeeInvalid && !priorityInvalid &&
        maxFee.trim().isNotEmpty() && priority.trim().isNotEmpty() &&
        !priorityLeMaxFee(priority, maxFee)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = maxFee,
                onValueChange = onMaxFeeChange,
                label = { Text("Max fee") },
                suffix = { Text("gwei") },
                singleLine = true,
                isError = maxFeeInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = priority,
                onValueChange = onPriorityChange,
                label = { Text("Priority") },
                suffix = { Text("gwei") },
                singleLine = true,
                isError = priorityInvalid || orderInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        if (orderInvalid) {
            Text(
                text = "Priority must be ≤ max fee.",
                color = WalletColors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun parsesAsGwei(decimal: String): Boolean {
    return try {
        EthFormat.gweiToWei(decimal)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}

internal fun priorityLeMaxFee(priority: String, maxFee: String): Boolean {
    val c = EthFormat.compareDecimal(priority, maxFee) ?: return false
    return c <= 0
}
