package yuku.alkitab.base.dialog

import android.app.Activity
import android.content.Intent
import android.text.InputType
import android.text.TextUtils
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import java.util.Date
import yuku.alkitab.base.App
import yuku.alkitab.base.IsiActivity
import yuku.alkitab.base.S.db
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

        MaterialDialog(activity)
            .positiveButton(R.string.ok)
            .input(
                hint = activity.getString(R.string.pm_progress_name),
                prefill = caption,
                maxLength = 32,
                inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
                allowEmpty = true,
            ) { _, input ->
                val name: String = input.toString()
                if (TextUtils.getTrimmedLength(name) == 0) {
                    progressMark.caption = null
                } else {
                    progressMark.caption = name
                }
                progressMark.modifyTime = Date()
                db.insertOrUpdateProgressMark(progressMark)

                // Since updating database is the responsibility here,
                // announcing it will also be here.
                App.getLbm().sendBroadcast(Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED))
                listener.onOked()
            }
            .negativeButton(R.string.delete) {
                MaterialDialog(activity).show {
                    message(text = TextUtils.expandTemplate(activity.getText(R.string.pm_delete_progress_confirm), caption))
                    positiveButton(R.string.ok) {
                        progressMark.ari = 0
                        progressMark.caption = null
                        progressMark.modifyTime = Date()
                        db.insertOrUpdateProgressMark(progressMark)

                        // Since updating database is the responsibility here,
                        // announcing it will also be here.
                        App.getLbm().sendBroadcast(Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED))
                        listener.onDeleted()
                    }
                    negativeButton(R.string.cancel)
                }
            }
            .show()
    }

    interface Listener {
        fun onOked()
        fun onDeleted()
    }
}