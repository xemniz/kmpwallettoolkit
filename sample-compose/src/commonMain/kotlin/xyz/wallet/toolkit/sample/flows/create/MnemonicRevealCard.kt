package xyz.wallet.toolkit.sample.flows.create

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Placeholder stub for Phase 1 — full implementation in Phase 2. */
@Composable
fun MnemonicRevealCard(
    mnemonic: String,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(text = "MnemonicRevealCard", modifier = modifier.fillMaxWidth())
}
