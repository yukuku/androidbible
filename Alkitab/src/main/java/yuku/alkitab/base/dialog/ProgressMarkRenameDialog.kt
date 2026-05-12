package yuku.alkitab.base.dialog

import android.app.Activity
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Date
import yuku.alkitab.base.App
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.debug.R
import yuku.alkitab.model.ProgressMark

object ProgressMarkRenameDialog : DialogFragment() {
    fun show(activity: Activity, progressMark: ProgressMark, listener: Listener) {
        val caption = if (!TextUtils.isEmpty(progressMark.caption)) {
            progressMark.caption
        } else {
            activity.getString(AttributeView.getDefaultProgressMarkStringResource(progressMark.preset_id))
        }

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_input, null, false)
        val til = dialogView.findViewById<TextInputLayout>(R.id.tilInput)
        val et = dialogView.findViewById<TextInputEditText>(R.id.etInput)
        til.hint = activity.getString(R.string.pm_progress_name)
        til.counterMaxLength = 32
        til.isCounterEnabled = true
        et.inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        et.setText(caption)

        MaterialAlertDialogBuilder(activity)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name: String = et.text.toString()
                if (TextUtils.getTrimmedLength(name) == 0) {
                    progressMark.caption = null
                } else {
                    progressMark.caption = name
                }
                progressMark.modifyTime = Date()
                App.services.storage.db.insertOrUpdateProgressMark(progressMark)

                // Since updating database is the responsibility here,
                // announcing it will also be here.
                AppEvents.emitAttributeMapChanged()
                listener.onOked()
            }
            .setNegativeButton(R.string.delete) { _, _ ->
                MaterialAlertDialogBuilder(activity)
                    .setMessage(TextUtils.expandTemplate(activity.getText(R.string.pm_delete_progress_confirm), caption))
                    .setPositiveButton(R.string.ok) { _, _ ->
                        progressMark.ari = 0
                        progressMark.caption = null
                        progressMark.modifyTime = Date()
                        App.services.storage.db.insertOrUpdateProgressMark(progressMark)

                        // Since updating database is the responsibility here,
                        // announcing it will also be here.
                        AppEvents.emitAttributeMapChanged()
                        listener.onDeleted()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setView(dialogView)
            .show()
    }

    interface Listener {
        fun onOked()
        fun onDeleted()
    }
}
