package yuku.alkitab.base.dialog

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.customview.customView
import yuku.alkitab.base.S.db
import yuku.alkitab.debug.R

object LabelEditorDialog {
    @JvmStatic
    fun show(context: Context, initialText: String, title: String, okListener: OkListener) {
        val dialogView = View.inflate(context, R.layout.dialog_edit_label, null)

        val tCaption = dialogView.findViewById<EditText>(R.id.tCaption)
        tCaption.setText(initialText)

        val dialog = MaterialDialog(context)
            .customView(view = dialogView)
            .title(text = title)
            .positiveButton(R.string.ok) {
                okListener.onOk(tCaption.text.toString().trim())
            }
            .negativeButton(R.string.cancel)

        val window = dialog.window
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        dialog.show()

        dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)

        val allLabels = db.listAllLabels()
        tCaption.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (s.isBlank()) {
                    dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
                    return
                } else {
                    val newTitleTrimmed = s.toString().trim { it <= ' ' }
                    for (label in allLabels) {
                        if (label.title.trim() == newTitleTrimmed) {
                            dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
                            return
                        }
                    }
                }
                dialog.setActionButtonEnabled(WhichButton.POSITIVE, true)
            }
        })
    }

    fun interface OkListener {
        fun onOk(title: String?)
    }
}
