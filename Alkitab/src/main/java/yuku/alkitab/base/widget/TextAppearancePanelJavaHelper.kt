package yuku.alkitab.base.widget

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper.withAdapter

object TextAppearancePanelJavaHelper {
    @JvmStatic
    fun showColorThemeDialog(context: Context, adapter: MaterialDialogAdapterHelper.Adapter): MaterialDialog {
        return MaterialDialog(context).show {
            withAdapter(adapter)
        }
    }
}
