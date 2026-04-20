package yuku.alkitab.base.widget

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object MaterialDialogAdapterHelper {
    /**
     * Shows a dialog with a RecyclerView driven by [adapter].
     * The [adapter]'s [Adapter.dialog] and [Adapter.recyclerView] fields are set before the
     * dialog is shown so that items can dismiss it via [Adapter.dismissDialog].
     */
    @JvmStatic
    @JvmOverloads
    fun showDialogWithAdapter(context: Context, adapter: Adapter, title: CharSequence? = null): AlertDialog {
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .apply { if (title != null) setTitle(title) }
            .setView(recyclerView)
            .create()
        adapter.dialog = dialog
        adapter.recyclerView = recyclerView
        dialog.show()
        return dialog
    }

    abstract class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
        var dialog: AlertDialog? = null
        var recyclerView: RecyclerView? = null

        /** Call from an item click handler to dismiss the containing dialog. */
        fun dismissDialog() {
            dialog?.dismiss()
        }
    }
}
