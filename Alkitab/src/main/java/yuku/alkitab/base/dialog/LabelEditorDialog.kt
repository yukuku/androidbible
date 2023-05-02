package yuku.alkitab.base.dialog

import android.content.Context
import android.text.InputType
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.input.input
import yuku.alkitab.base.S.db
import yuku.alkitab.debug.R

private const val MAX_LABEL_LENGTH = 48

object LabelEditorDialog {
    @JvmStatic
    fun show(context: Context, initialText: String, title: String, okListener: OkListener) {
        val allLabels = db.listAllLabels()
        var input = ""

        val dialog = MaterialDialog(context)
            .title(text = title)
            .input(
                hintRes = R.string.nama_label_titikdua,
                prefill = initialText,
                inputType = InputType.TYPE_CLASS_TEXT,
                maxLength = MAX_LABEL_LENGTH,
                allowEmpty = false,
                waitForPositiveButton = false,
            ) { dialog, s ->
                input = s.toString().trim()

                if (s.isBlank() || s.length > MAX_LABEL_LENGTH) {
                    dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
                    return@input
                } else {
                    val newTitleTrimmed = s.toString().trim()
                    for (label in allLabels) {
                        if (label.title.trim() == newTitleTrimmed) {
                            dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
                            return@input
                        }
                    }
                }

                dialog.setActionButtonEnabled(WhichButton.POSITIVE, true)
            }
            .positiveButton(R.string.ok) {
                okListener.onOk(input)
            }
            .negativeButton(R.string.cancel)

        dialog.show()

        dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
    }

    fun interface OkListener {
        fun onOk(title: String?)
    }
}
