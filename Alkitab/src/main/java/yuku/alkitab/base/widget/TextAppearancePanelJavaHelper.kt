package yuku.alkitab.base.widget

import android.content.Context
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper

object TextAppearancePanelJavaHelper {
    @JvmStatic
    fun showColorThemeDialog(context: Context, adapter: MaterialDialogAdapterHelper.Adapter) {
        MaterialDialogAdapterHelper.showDialogWithAdapter(context, adapter)
    }
}
