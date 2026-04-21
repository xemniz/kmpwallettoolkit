package xyz.wallet.toolkit.sample.flows.send

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import xyz.wallet.toolkit.sample.format.EthFormat

/**
 * Decimal ETH input. Conversion to wei is deferred to the assembler via
 * [EthFormat.ethDecimalToWei]; this field only surfaces inline validity.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trimmed = value.trim()
    val isInvalid = trimmed.isNotEmpty() && !parsesAsPositiveEth(trimmed)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Amount") },
        placeholder = { Text("0.0") },
        suffix = { Text("ETH") },
        singleLine = true,
        isError = isInvalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

internal fun parsesAsPositiveEth(decimal: String): Boolean {
    return try {
        val wei = EthFormat.ethDecimalToWei(decimal)
        // "0" is parseable but not > 0; reject.
        wei.isNotEmpty() && wei != "0"
    } catch (_: IllegalArgumentException) {
        false
    }
}
