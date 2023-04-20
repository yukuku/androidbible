package yuku.alkitab.base.util

import android.app.Activity
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import java.util.concurrent.atomic.AtomicBoolean
import yuku.alkitab.base.widget.MaterialDialogProgressHelper.progress
import yuku.alkitab.debug.R

object ShareUrlJavaHelper {
    @JvmStatic
    fun showMaterialDialog(activity: Activity, callback: ShareUrl.Callback, done: AtomicBoolean): MaterialDialog {
        return MaterialDialog(activity).show {
            message(text = "Getting share URL…")
            progress(true, 0)
            negativeButton(R.string.cancel) { dialog ->
                if (done.getAndSet(true)) return@negativeButton
                done.set(true)
                callback.onUserCancel()
                dialog.dismiss()
                callback.onFinally()
            }
            onDismiss { dialog ->
                if (done.getAndSet(true)) return@onDismiss
                callback.onUserCancel()
                dialog.dismiss()
                callback.onFinally()
            }
        }
    }
}