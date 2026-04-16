package yuku.alkitab.base.ac

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.debug.R

/**
 * Use this class to show an alert dialog if you don't have an existing activity to
 * show the dialog on.
 *
 * This starts a transparent activity and then shows an alert dialog on top
 * of the transparent activity.
 */
class AlertDialogActivity : BaseActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val title = intent.getStringExtra(EXTRA_TITLE)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
        val negative = intent.getStringExtra(EXTRA_NEGATIVE)
        val positive = intent.getStringExtra(EXTRA_POSITIVE) ?: getString(android.R.string.ok)
        val inputType = intent.getIntExtra(EXTRA_INPUT_TYPE, InputType.TYPE_CLASS_TEXT)
        val inputHint = intent.getStringExtra(EXTRA_INPUT_HINT)
        val launch = intent.getParcelableExtra<Intent>(EXTRA_LAUNCH)

        val builder = MaterialAlertDialogBuilder(this)
        if (title != null) builder.setTitle(title)
        if (message != null) builder.setMessage(message)

        var etInput: TextInputEditText? = null
        if (inputHint != null) {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null, false)
            val til = dialogView.findViewById<TextInputLayout>(R.id.tilInput)
            etInput = dialogView.findViewById(R.id.etInput)
            til.hint = inputHint
            etInput.inputType = inputType
            builder.setView(dialogView)
        }

        builder.setPositiveButton(positive) { _, _ ->
            if (inputHint != null) {
                val returnIntent = Intent()
                returnIntent.putExtra(EXTRA_INPUT, etInput?.text.toString())
                setResult(RESULT_OK, returnIntent)
            } else {
                val returnIntent = Intent()
                setResult(RESULT_OK, returnIntent)
                if (launch != null) {
                    try {
                        startActivity(launch)
                    } catch (e: ActivityNotFoundException) {
                        MaterialAlertDialogBuilder(this@AlertDialogActivity)
                            .setMessage("Activity was not found for intent: $launch")
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
            }
            finish()
        }
        if (negative != null) {
            builder.setNegativeButton(negative) { _, _ -> finish() }
        }
        val dialog = builder.create()
        dialog.setOnDismissListener { finish() }
        dialog.show()
    }

    companion object {
        // Inputs
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_NEGATIVE = "negative"
        private const val EXTRA_POSITIVE = "positive"
        private const val EXTRA_INPUT_TYPE = "input_type"
        private const val EXTRA_INPUT_HINT = "input_hint"
        private const val EXTRA_LAUNCH = "launch"

        // Output
        const val EXTRA_INPUT = "input"

        @JvmStatic
        fun createOkIntent(title: String?, message: String?): Intent {
            return Intent(App.context, AlertDialogActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
        }

        @JvmStatic
        fun createInputIntent(
            title: String?,
            message: String?,
            negativeButtonText: String?,
            positiveButtonText: String?,
            inputType: Int,
            inputHint: String?,
        ): Intent {
            return Intent(App.context, AlertDialogActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_NEGATIVE, negativeButtonText)
                .putExtra(EXTRA_POSITIVE, positiveButtonText)
                .putExtra(EXTRA_INPUT_TYPE, inputType)
                .putExtra(EXTRA_INPUT_HINT, inputHint)
        }
    }
}
