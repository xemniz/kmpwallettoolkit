package xyz.wallet.toolkit.sample.flows.create

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Placeholder stub for Phase 1 — full implementation in Phase 2. */
@Composable
fun ConfirmWordStep(
    targetIndexOneBased: Int,
    options: List<String>,
    onPick: (String) -> Unit,
    errorHint: String?,
    modifier: Modifier = Modifier,
) {
    Text(text = "ConfirmWordStep #$targetIndexOneBased", modifier = modifier.fillMaxWidth())
}
