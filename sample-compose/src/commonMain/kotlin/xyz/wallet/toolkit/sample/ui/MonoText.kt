package xyz.wallet.toolkit.sample.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import xyz.wallet.toolkit.sample.theme.MonoTextStyle
import xyz.wallet.toolkit.sample.theme.WalletColors

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = WalletColors.textPrimary,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MonoTextStyle,
    )
}
