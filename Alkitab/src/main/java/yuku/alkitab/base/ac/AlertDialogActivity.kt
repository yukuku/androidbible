package yuku.alkitab.base.ac

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.input.input
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

        val builder = MaterialDialog(this)
        if (title != null) {
            builder.title(text = title)
        }
        if (message != null) {
            builder.message(text = message)
        }
        if (inputHint != null) {
            builder.input(inputType = inputType, hint = inputHint) { dialog, input ->
                val returnIntent = Intent()
                returnIntent.putExtra(EXTRA_INPUT, input.toString())
                setResult(RESULT_OK, returnIntent)
                finish()
            }
        }
        builder.positiveButton(text = positive) {
            if (inputHint == null) {
                val returnIntent = Intent()
                setResult(RESULT_OK, returnIntent)
                if (launch != null) {
                    try {
                        startActivity(launch)
                    } catch (e: ActivityNotFoundException) {
                        MaterialDialog(this@AlertDialogActivity).show {
                            message(text = "Actvity was not found for intent: $launch")
                            positiveButton(R.string.ok)
                        }
                    }
                }
                finish()
            }
        }
        if (negative != null) {
            builder.negativeButton(text = negative) { finish() }
        }
        builder.onDismiss { finish() }
        builder.show()
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
