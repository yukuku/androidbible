package yuku.alkitab.base.dialog

import android.content.Context
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import yuku.alkitab.base.dialog.TypeBookmarkDialog.LabelAdapter
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper.withAdapter
import yuku.alkitab.debug.R
import yuku.alkitab.model.Marker

internal object TypeBookmarkDialogJavaHelper {
    @JvmStatic
    fun showAddLabelDialog(context: Context, labelAdapter: LabelAdapter) {
        MaterialDialog(context)
            .title(R.string.add_label_title)
            .withAdapter(labelAdapter)
            .show()
    }

    @JvmStatic
    fun showBookmarkDialog(
        context: Context,
        marker: Marker?,
        reference: String,
        dialogView: View,
        onOk: () -> Unit,
        onDelete: (Marker?) -> Unit,
    ): MaterialDialog {
        return MaterialDialog(context).show {
            customView(view = dialogView)
            title(text = reference)
            icon(R.drawable.ic_attr_bookmark)
            positiveButton(R.string.ok) { onOk() }
            neutralButton(R.string.delete) { onDelete(marker) }
        }
    }
}
