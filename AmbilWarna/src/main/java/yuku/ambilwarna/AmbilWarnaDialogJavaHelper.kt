package yuku.ambilwarna

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object AmbilWarnaDialogJavaHelper {
    @JvmStatic
    fun buildMaterialDialog(ambilWarnaDialog: AmbilWarnaDialog, dialogView: android.view.View, context: Context, listener: AmbilWarnaDialog.OnAmbilWarnaListener, colorGetter: () -> Int): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                listener.onOk(ambilWarnaDialog, colorGetter())
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                listener.onCancel(ambilWarnaDialog)
            }
            .create()
        dialog.setOnDismissListener { listener.onCancel(ambilWarnaDialog) }
        return dialog
    }
}
