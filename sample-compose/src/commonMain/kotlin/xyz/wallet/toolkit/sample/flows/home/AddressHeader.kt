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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText

/**
 * Renders the active wallet's EVM address as a tap-to-copy affordance.
 *
 * Address casing is normalized to lowercase at both display and copy sites
 * (CLAUDE.md §4.7). The shortened form is display-only; the clipboard
 * always receives the full lowercase address.
 */
@Composable
fun AddressHeader(
    address: String,
    modifier: Modifier = Modifier,
) {
    val lower = address.lowercase()
    val shortened = shorten(lower)
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

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
                clipboard.setText(AnnotatedString(lower))
                copied = true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MonoText(text = shortened)
        Text(
            text = if (copied) "Copied" else "Tap to copy",
            color = WalletColors.textSecondary,
        )
    }
}

private fun shorten(addr: String): String {
    if (addr.length < 10) return addr
    return "${addr.take(6)}…${addr.takeLast(4)}"
}
