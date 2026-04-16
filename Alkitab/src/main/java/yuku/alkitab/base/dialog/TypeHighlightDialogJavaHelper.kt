package yuku.alkitab.base.dialog

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.alkitab.debug.R

object TypeHighlightDialogJavaHelper {
    /**
     * Shows the highlight dialog using [dialogView] (pre-inflated by the caller).
     * Callers must inflate [R.layout.dialog_edit_highlight] themselves and pass it here so
     * they retain a direct reference to the view hierarchy without needing [getCustomView].
     */
    @JvmStatic
    fun showHighlightDialog(
        context: Context,
        dialogView: View,
        title: String?,
        onOk: () -> Unit,
        onDelete: () -> Unit,
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setIcon(R.drawable.ic_attr_highlight)
            .setPositiveButton(R.string.ok) { _, _ -> onOk() }
            .setNeutralButton(R.string.delete) { _, _ -> onDelete() }
            .apply { if (title != null) setTitle(title) }
            .show()
    }
}
