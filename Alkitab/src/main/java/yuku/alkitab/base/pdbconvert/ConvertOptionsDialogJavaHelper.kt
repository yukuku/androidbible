package yuku.alkitab.base.pdbconvert

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.alkitab.debug.R

object ConvertOptionsDialogJavaHelper {
    /**
     * Builds the PDB convert options dialog using [dialogView] (pre-inflated by the caller).
     * Callers must inflate [R.layout.dialog_pdbconvert_options] themselves and pass it here.
     */
    @JvmStatic
    fun buildMaterialDialog(context: Context, dialogView: View, onOk: () -> Unit): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setTitle(R.string.pdb_file_options)
            .setPositiveButton(R.string.ok) { _, _ -> onOk() }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }
}
