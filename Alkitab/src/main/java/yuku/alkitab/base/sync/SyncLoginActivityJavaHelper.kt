package yuku.alkitab.base.sync

import android.content.Context
import android.widget.EditText
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import yuku.alkitab.debug.R

object SyncLoginActivityJavaHelper {
    @JvmStatic
    fun confirmPassword(context: Context, correctPassword: String, whenCorrect: Runnable) {
        MaterialDialog(context)
            .customView(R.layout.dialog_sync_confirm_password, scrollable = false)
            .positiveButton(R.string.ok) { dialog ->
                val tPassword2: EditText = dialog.getCustomView().findViewById(R.id.tPassword2)
                val password2 = tPassword2.text.toString()
                if (password2 != correctPassword) {
                    MaterialDialog(context).show {
                        message(R.string.sync_login_form_passwords_do_not_match)
                        positiveButton(R.string.ok)
                    }
                    return@positiveButton
                }
                whenCorrect.run()
            }
            .show()
    }

    @JvmStatic
    fun showProgressDialog(context: Context, message: String): MaterialDialog {
        return MaterialDialog(context).show {
            message(text = message)
            cancelable(false)
        }
    }
}