package yuku.alkitab.base.pdbconvert

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import yuku.alkitab.debug.R

object ConvertOptionsDialogJavaHelper {
    @JvmStatic
    fun buildMaterialDialog(context: Context, onOk: () -> Unit) =
        MaterialDialog(context)
            .customView(R.layout.dialog_pdbconvert_options)
            .title(R.string.pdb_file_options)
            .positiveButton(R.string.ok) { onOk() }
            .negativeButton(R.string.cancel)
}
