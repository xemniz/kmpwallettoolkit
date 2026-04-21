package xyz.wallet.toolkit.sample.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object WalletColors {
    val background: Color = Color(0xFFF2F1EE)
    val surface: Color = Color(0xFFFFFFFF)
    val outline: Color = Color(0xFF1A1A1A)
    val textPrimary: Color = Color(0xFF1A1A1A)
    val textSecondary: Color = Color(0xFF6E6E6E)
    val accent: Color = Color(0xFFFF4A1C)
}

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = WalletColors.accent,
        onPrimary = Color.White,
        background = WalletColors.background,
        onBackground = WalletColors.textPrimary,
        surface = WalletColors.surface,
        onSurface = WalletColors.textPrimary,
        outline = WalletColors.outline,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = WalletTypography,
        content = content,
    )
}
