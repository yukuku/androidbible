package yuku.alkitab.base.dialog

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import yuku.alkitab.debug.R

object TypeHighlightDialogJavaHelper {
    @JvmStatic
    fun showHighlightDialog(
        context: Context,
        title: String?,
        onOk: () -> Unit,
        onDelete: () -> Unit,
    ) = MaterialDialog(context).show {
        customView(R.layout.dialog_edit_highlight)
        icon(R.drawable.ic_attr_highlight)
        positiveButton(R.string.ok) { onOk() }
        neutralButton(R.string.delete) { onDelete() }
        if (title != null) {
            title(text = title)
        }
    }
}
