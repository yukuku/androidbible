package yuku.alkitab.base.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import yuku.alkitab.base.App

object ClipboardUtil {
    @JvmStatic
    fun copyToClipboard(text: CharSequence) {
        val clipboardManager = App.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))
    }
}