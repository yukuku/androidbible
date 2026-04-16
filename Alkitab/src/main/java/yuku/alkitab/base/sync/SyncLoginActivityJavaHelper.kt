package yuku.alkitab.base.sync

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.alkitab.debug.R

object SyncLoginActivityJavaHelper {
    @JvmStatic
    fun confirmPassword(context: Context, correctPassword: String, whenCorrect: Runnable) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_sync_confirm_password, null, false)
        MaterialAlertDialogBuilder(context)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val tPassword2: EditText = view.findViewById(R.id.tPassword2)
                val password2 = tPassword2.text.toString()
                if (password2 != correctPassword) {
                    MaterialAlertDialogBuilder(context)
                        .setMessage(R.string.sync_login_form_passwords_do_not_match)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                    return@setPositiveButton
                }
                whenCorrect.run()
            }
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
