package xyz.wallet.toolkit.sample.flows.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.wallet.toolkit.sample.theme.WalletColors
import xyz.wallet.toolkit.sample.ui.MonoText

/**
 * Mnemonic reveal card — shows the 12 words in a 3-column grid. A frosted
 * overlay blocks the text until the card is tapped; `onReveal` is invoked on
 * the first tap so the caller can toggle `revealed = true`.
 */
@Composable
fun MnemonicRevealCard(
    mnemonic: String,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = mnemonic.trim().split(Regex("\\s+"))
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, WalletColors.outline, shape)
            .background(WalletColors.background)
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val rows = words.chunked(3)
            rows.forEachIndexed { rowIdx, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    row.forEachIndexed { colIdx, word ->
                        val number = rowIdx * 3 + colIdx + 1
                        MonoText(
                            text = "$number. $word",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        if (!revealed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Color(0xF2F2F1EE))
                    .clickable { onReveal() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Tap to reveal your recovery phrase",
                    color = WalletColors.textSecondary,
                )
            }
        }
    }
}
