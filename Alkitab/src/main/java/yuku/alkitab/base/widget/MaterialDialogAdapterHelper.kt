package yuku.alkitab.base.widget

import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog

object MaterialDialogAdapterHelper {
    @JvmStatic
    fun MaterialDialog.Builder.showWithAdapter(adapter: Adapter): MaterialDialog {
        val dialog = this
            .adapter(adapter, null)
            .build()
        adapter.dialog = dialog
        dialog.show()
        return dialog
    }

    abstract class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
        var dialog: MaterialDialog? = null

        fun dismissDialog() {
            dialog?.dismiss()
        }
    }
}
