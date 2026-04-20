package yuku.alkitab.base.dialog

import android.content.Context
import android.content.DialogInterface
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import yuku.alkitab.base.S.db
import yuku.alkitab.debug.R

private const val MAX_LABEL_LENGTH = 48

object LabelEditorDialog {
    @JvmStatic
    fun show(context: Context, initialText: String, title: String, okListener: OkListener) {
        val allLabels = db.listAllLabels()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_input, null, false)
        val til = dialogView.findViewById<TextInputLayout>(R.id.tilInput)
        val et = dialogView.findViewById<TextInputEditText>(R.id.etInput)

        til.hint = context.getString(R.string.nama_label_titikdua)
        til.isCounterEnabled = true
        til.counterMaxLength = MAX_LABEL_LENGTH
        et.inputType = InputType.TYPE_CLASS_TEXT
        et.filters = arrayOf(InputFilter.LengthFilter(MAX_LABEL_LENGTH))
        et.setText(initialText)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                okListener.onOk(et.text.toString().trim())
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        fun isValid(text: CharSequence?): Boolean {
            val trimmed = text?.toString()?.trim().orEmpty()
            return trimmed.isNotEmpty() && trimmed.length <= MAX_LABEL_LENGTH &&
                allLabels.none { it.title.trim() == trimmed }
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = isValid(s)
            }
        })

        dialog.show()
        // Derive initial button state from initialText — e.g. a caller pre-filling a valid,
        // non-duplicate name should get an immediately-clickable OK.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = isValid(initialText)
    }

    fun interface OkListener {
        fun onOk(title: String?)
    }
}
