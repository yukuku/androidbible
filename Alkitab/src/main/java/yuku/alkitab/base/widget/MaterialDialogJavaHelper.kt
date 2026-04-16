package yuku.alkitab.base.widget

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.alkitab.debug.R

/**
 * For simple cases for showing a message and an ok button for Java callers.
 */
object MaterialDialogJavaHelper {
    @JvmStatic
    @JvmOverloads
    fun showOkDialog(context: Context, message: String, positiveText: String? = null, onPositive: () -> Unit = {}, negativeText: String? = null): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setMessage(message)
            .setPositiveButton(positiveText ?: context.getString(R.string.ok)) { _, _ -> onPositive() }
            .apply { if (negativeText != null) setNegativeButton(negativeText, null) }
            .show()
    }

    @JvmStatic
    fun showProgressDialog(context: Context, message: String): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setMessage(message)
            .setCancelable(false)
            .show()
    }
}
