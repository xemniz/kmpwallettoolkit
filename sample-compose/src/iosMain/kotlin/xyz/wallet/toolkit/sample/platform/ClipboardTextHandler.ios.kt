package xyz.wallet.toolkit.sample.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPasteboard

@Composable
actual fun rememberClipboardTextHandler(): ClipboardTextHandler {
    return remember { IosClipboardTextHandler }
}

private object IosClipboardTextHandler : ClipboardTextHandler {
    override fun setText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun getText(): String? {
        return UIPasteboard.generalPasteboard.string
    }
}
