package xyz.wallet.toolkit.sample.platform

import androidx.compose.runtime.Composable

interface ClipboardTextHandler {
    fun setText(text: String)
    fun getText(): String?
}

@Composable
expect fun rememberClipboardTextHandler(): ClipboardTextHandler
