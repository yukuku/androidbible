package yuku.alkitab.base.widget

import com.afollestad.materialdialogs.MaterialDialog

object MaterialDialogProgressHelper {
    @Suppress("UNUSED_PARAMETER")
    fun MaterialDialog.progress(indeterminate: Boolean, progress: Int = 0) {
        // this is a no-op because MaterialDialog 2.x doesn't support progress dialog
    }
}
