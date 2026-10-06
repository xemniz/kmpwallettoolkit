package xyz.wallet.toolkit.sample.flows.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.wallet.toolkit.sample.platform.rememberClipboardTextHandler
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText

@Composable
fun AddressHeader(
    address: String,
    modifier: Modifier = Modifier,
) {
    val lower = address.lowercase()
    val clipboard = rememberClipboardTextHandler()
    var copied by remember(lower) { mutableStateOf(false) }

    if (copied) {
        LaunchedEffect(copied) {
            delay(1500L)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(lower)
                copied = true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MonoText(text = lower)
        Text(
            text = if (copied) "Copied" else "Tap to copy",
            color = WalletColors.textSecondary,
        )
    }
}
