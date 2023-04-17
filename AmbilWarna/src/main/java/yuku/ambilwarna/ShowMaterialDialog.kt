package yuku.ambilwarna

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView

internal object ShowMaterialDialog {
    @JvmStatic
    fun buildMaterialDialog(ambilWarnaDialog: AmbilWarnaDialog, context: Context, listener: AmbilWarnaDialog.OnAmbilWarnaListener, colorGetter: () -> Int): MaterialDialog {
        return MaterialDialog(context)
            .customView(R.layout.ambilwarna_dialog, scrollable = false)
            .positiveButton(android.R.string.ok) {
                listener.onOk(ambilWarnaDialog, colorGetter())
            }
            .negativeButton(android.R.string.cancel) {
                listener.onCancel(ambilWarnaDialog)
            }
            .onDismiss { listener.onCancel(ambilWarnaDialog) }
    }
}