package yuku.alkitab.base.widget

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import yuku.alkitab.base.widget.MaterialDialogProgressHelper.progress
import yuku.alkitab.debug.R

/**
 * For simple cases for showing a message and an ok button for Java callers.
 */
object MaterialDialogJavaHelper {
    @JvmStatic
    @JvmOverloads
    fun showOkDialog(context: Context, message: String, positiveText: String? = null, onPositive: () -> Unit = {}, negativeText: String? = null): MaterialDialog {
        return MaterialDialog(context).show {
            message(text = message)
            positiveButton(text = positiveText ?: context.getString(R.string.ok)) {
                onPositive()
            }
            if (negativeText != null) {
                negativeButton(text = negativeText)
            }
        }
    }

    @JvmStatic
    fun showProgressDialog(context: Context, message: String): MaterialDialog {
        return MaterialDialog(context).show {
            message(text = message)
            progress(true, 0)
        }
    }
}
