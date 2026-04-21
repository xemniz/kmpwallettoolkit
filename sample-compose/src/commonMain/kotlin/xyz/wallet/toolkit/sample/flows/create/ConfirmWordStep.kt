package xyz.wallet.toolkit.sample.flows.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText

/**
 * Confirm-word step: prompts the user to pick which word was at the target
 * position. The four options are provided pre-shuffled by the caller. A
 * subdued `errorHint` is shown underneath when present — the challenge is
 * intentionally NOT regenerated on an incorrect pick (see spec §Design.3).
 */
@Composable
fun ConfirmWordStep(
    targetIndexOneBased: Int,
    options: List<String>,
    onPick: (String) -> Unit,
    errorHint: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Which was word #$targetIndexOneBased?",
            color = WalletColors.textPrimary,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { word ->
                OptionChip(word = word, onClick = { onPick(word) })
            }
        }
        if (errorHint != null) {
            Text(
                text = errorHint,
                color = WalletColors.textSecondary,
            )
        }
    }
}

@Composable
private fun OptionChip(word: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, WalletColors.outline, shape)
            .background(WalletColors.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        MonoText(text = word)
    }
}
