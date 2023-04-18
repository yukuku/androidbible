package yuku.alkitab.ribka

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.util.PatternsCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.widget.MaterialDialogProgressHelper.progress
import yuku.alkitab.base.widget.VerseRendererJavaHelper
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R

class RibkaReportActivity : BaseActivity() {
    private lateinit var tRibkaVerseText: TextView
    private lateinit var tRibkaReference: TextView
    private lateinit var oRibkaCategoryTypo: RadioButton
    private lateinit var oRibkaCategoryWord: RadioButton
    private lateinit var oRibkaCategorySentence: RadioButton
    private lateinit var oRibkaCategoryContent: RadioButton
    private lateinit var oRibkaCategoryOthers: RadioButton
    private lateinit var tRibkaSuggestionContainer: TextInputLayout
    private lateinit var tRibkaSuggestion: EditText
    private lateinit var tRibkaEmailContainer: TextInputLayout
    private lateinit var tRibkaEmail: EditText
    private lateinit var tRibkaRemarks: EditText
    private lateinit var bRibkaSend: View

    var ari: Int = 0
    lateinit var verseText: String
    private var versionDescription: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ribka_activity_report)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tRibkaVerseText = findViewById(R.id.tRibkaVerseText)
        tRibkaReference = findViewById(R.id.tRibkaReference)
        oRibkaCategoryTypo = findViewById(R.id.oRibkaCategoryTypo)
        oRibkaCategoryWord = findViewById(R.id.oRibkaCategoryWord)
        oRibkaCategorySentence = findViewById(R.id.oRibkaCategorySentence)
        oRibkaCategoryContent = findViewById(R.id.oRibkaCategoryContent)
        oRibkaCategoryOthers = findViewById(R.id.oRibkaCategoryOthers)
        tRibkaSuggestionContainer = findViewById(R.id.tRibkaSuggestionContainer)
        tRibkaSuggestion = findViewById(R.id.tRibkaSuggestion)
        tRibkaEmailContainer = findViewById(R.id.tRibkaEmailContainer)
        tRibkaEmail = findViewById(R.id.tRibkaEmail)
        tRibkaRemarks = findViewById(R.id.tRibkaRemarks)
        bRibkaSend = findViewById(R.id.bRibkaSend)

        ari = intent.getIntExtra("ari", 0)
        val reference = intent.getStringExtra("reference") ?: return finish()
        verseText = intent.getStringExtra("verseText") ?: return finish()
        versionDescription = intent.getStringExtra("versionDescription")

        tRibkaReference.text = reference
        VerseRendererJavaHelper.render(
            lText = tRibkaVerseText,
            ari = ari,
            text = verseText
        )

        tRibkaSuggestion.setText(FormattedVerseText.removeSpecialCodes(verseText))

        val defaultEmail = Preferences.getString(R.string.pref_syncAccountName_key)
        if (defaultEmail != null) {
            tRibkaEmail.setText(defaultEmail)
        }

        bRibkaSend.setOnClickListener { bRibkaSend_click() }
    }

    private fun bRibkaSend_click() {
        tRibkaSuggestionContainer.error = null
        tRibkaEmailContainer.error = null

        val category = when {
            oRibkaCategoryTypo.isChecked -> "typo"
            oRibkaCategoryWord.isChecked -> "word"
            oRibkaCategorySentence.isChecked -> "sentence"
            oRibkaCategoryContent.isChecked -> "content"
            oRibkaCategoryOthers.isChecked -> "others"
            else -> {
                MaterialDialog(this).show {
                    message(R.string.ribka_category_error)
                    positiveButton(R.string.ok)
                }
                null
            }
        } ?: return

        val suggestion = tRibkaSuggestion.text.toString()
        val remarks = tRibkaRemarks.text.toString().trim()

        fun sameAsOriginal(): Boolean {
            return (FormattedVerseText.removeSpecialCodes(verseText)?.trim() == suggestion.trim())
        }

        // must have a change in suggestion OR put some remarks
        if ((suggestion.isBlank() || sameAsOriginal()) && remarks.isBlank()) {
            tRibkaSuggestionContainer.error = getText(R.string.ribka_suggestion_error)
            return
        }

        val email = tRibkaEmail.text.toString().trim()
        if (email.isEmpty() || !PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
            tRibkaEmailContainer.error = getText(R.string.ribka_email_error)
            return
        }

        val form = FormBody.Builder()
        form.add("reportPresetName", "in-ayt")
        form.add("reportCategory", category)
        form.add("reportEmail", email)
        form.add("reportAri", "$ari")
        form.add("reportVerseText", verseText)
        form.add("reportSuggestion", suggestion)
        form.add("reportRemarks", remarks)
        form.add("reportVersionDescription", versionDescription.orEmpty())

        val pd = MaterialDialog(this).show {
            message(R.string.ribka_sending_progress)
            cancelable(false)
            progress(true, 0)
        }

        Connections.okHttp.newCall(Request.Builder().url(BuildConfig.RIBKA_FUNCTIONS_HOST + "addIssue").post(form.build()).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                pd.dismiss()

                runOnUiThread {
                    MaterialDialog(this@RibkaReportActivity).show {
                        message(R.string.ribka_send_error)
                        positiveButton(R.string.ok)
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                pd.dismiss()

                if (response.isSuccessful) {
                    runOnUiThread {
                        MaterialDialog(this@RibkaReportActivity).show {
                            message(R.string.ribka_send_success)
                            positiveButton(R.string.ok)
                            onDismiss { finish() }
                        }
                    }
                } else {
                    runOnUiThread {
                        MaterialDialog(this@RibkaReportActivity).show {
                            message(text = TextUtils.expandTemplate(getString(R.string.ribka_send_failure), "${response.code} ${response.body?.string()}"))
                            positiveButton(R.string.ok)
                        }
                    }
                }
            }
        })
    }

    companion object {
        @JvmStatic
        fun createIntent(ari: Int, reference: String, verseText: String, versionDescription: String?): Intent =
            Intent(App.context, RibkaReportActivity::class.java)
                .putExtra("ari", ari)
                .putExtra("reference", reference)
                .putExtra("verseText", verseText)
                .putExtra("versionDescription", versionDescription)
    }
}
