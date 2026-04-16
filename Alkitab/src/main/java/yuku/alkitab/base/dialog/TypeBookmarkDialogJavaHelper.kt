package yuku.alkitab.base.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper
import yuku.alkitab.debug.R
import yuku.alkitab.model.Marker

object TypeBookmarkDialogJavaHelper {
    @JvmStatic
    fun showAddLabelDialog(context: Context, adapter: MaterialDialogAdapterHelper.Adapter) {
        MaterialDialogAdapterHelper.showDialogWithAdapter(context, adapter, context.getString(R.string.add_label_title))
    }

    @JvmStatic
    fun showBookmarkDialog(
        context: Context,
        marker: Marker?,
        reference: String,
        dialogView: View,
        onOk: () -> Unit,
        onDelete: (Marker?) -> Unit,
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setTitle(reference)
            .setIcon(R.drawable.ic_attr_bookmark)
            .setPositiveButton(R.string.ok) { _, _ -> onOk() }
            .setNeutralButton(R.string.delete) { _, _ -> onDelete(marker) }
            .show()
    }
}
