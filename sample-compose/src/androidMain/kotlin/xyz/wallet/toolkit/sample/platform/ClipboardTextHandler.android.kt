package xyz.wallet.toolkit.sample.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberClipboardTextHandler(): ClipboardTextHandler {
    val context = LocalContext.current
    return remember(context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        AndroidClipboardTextHandler(clipboard)
    }
}

private class AndroidClipboardTextHandler(
    private val clipboard: ClipboardManager,
) : ClipboardTextHandler {
    override fun setText(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("wallet address", text))
    }

    override fun getText(): String? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(null)?.toString()
    }
}
