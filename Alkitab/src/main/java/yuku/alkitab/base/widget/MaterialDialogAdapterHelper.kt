package yuku.alkitab.base.widget

import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.customListAdapter

object MaterialDialogAdapterHelper {
    @JvmStatic
    fun MaterialDialog.withAdapter(adapter: Adapter): MaterialDialog {
        adapter.dialog = customListAdapter(adapter)
        return this
    }

    abstract class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
        var dialog: MaterialDialog? = null

        fun dismissDialog() {
            dialog?.dismiss()
        }
    }
}
