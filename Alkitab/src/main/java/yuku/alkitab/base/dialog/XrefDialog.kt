package yuku.alkitab.base.dialog

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import java.util.Locale
import kotlin.properties.Delegates.notNull
import yuku.alkitab.base.S
import yuku.alkitab.base.dialog.base.BaseDialog
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Appearances.applyTextAppearance
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.base.verses.VersesControllerImpl
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.base.verses.VersesListeners
import yuku.alkitab.base.verses.VersesUiModel
import yuku.alkitab.base.widget.FormattedTextRenderer
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.model.XrefEntry
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

private const val TAG = "XrefDialog"
private const val EXTRA_arif = "arif"

class XrefDialog : BaseDialog() {
    /**
     * This dialog does not support fragment recreation,
     * it will just be closed when the dialog is recreated without calling [init].
     */
    private var initted = false
    private var arif_source by notNull<Int>()
    private var xrefEntry: XrefEntry? = null
    private lateinit var verseSelectedListener: (arif_source: Int, ari_target: Int) -> Unit

    private lateinit var tXrefText: TextView
    private lateinit var versesController: VersesController

    private lateinit var sourceVersion: Version
    private lateinit var sourceVersionId: String
    private var textSizeMult by notNull<Float>()

    private var displayedLinkPos = -1 // -1 indicates that we should auto-select the first link
    private val displayedRealAris = IntArrayList()

    /**
     * This must be called after [newInstance].
     */
    fun init(sourceVersion: Version, sourceVersionId: String, verseSelectedListener: (arif_source: Int, ari_target: Int) -> Unit) {
        this.sourceVersion = sourceVersion
        this.sourceVersionId = sourceVersionId
        this.textSizeMult = S.getDb().getPerVersionSettings(sourceVersionId).fontSizeMultiplier
        this.verseSelectedListener = verseSelectedListener
        this.initted = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_TITLE, 0)

        if (!initted) {
            dismiss()
            return
        }

        // TODO appcompat 1.1.0: change to requireArguments()
        val arguments = requireNotNull(arguments)
        this.arif_source = arguments.getInt(EXTRA_arif)
        this.xrefEntry = sourceVersion.getXrefEntry(arif_source) ?: run {
            dismiss()
            return
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (!initted) return null

        return inflater.inflate(R.layout.dialog_xref, container, false).apply {
            tXrefText = findViewById(R.id.tXrefText)
            setBackgroundColor(S.applied().backgroundColor)

            versesController = VersesControllerImpl(
                findViewById(R.id.lsView),
                "xref",
                VersesDataModel.EMPTY,
                VersesUiModel.EMPTY.copy(
                    verseSelectionMode = VersesController.VerseSelectionMode.singleClick,
                    isVerseNumberShown = true
                ),
                VersesListeners.EMPTY.copy(
                    selectedVersesListener = selectedVersesListener,
                    inlineLinkSpanFactory_ = { type, arif ->
                        object : VerseInlineLinkSpan(type, arif) {
                            override fun onClick(type: Type, arif: Int) {
                                val ari = arif ushr 8
                                selectedVersesListener.onVerseSingleClick(Ari.toVerse(ari))
                            }
                        }
                    }
                ),
            )

            tXrefText.movementMethod = LinkMovementMethod.getInstance()
            if (xrefEntry != null) {
                renderXrefText()
            } else {
                MaterialDialog.Builder(inflater.context)
                    .content(String.format(Locale.US, "Error: xref at arif 0x%08x couldn't be loaded", arif_source))
                    .positiveText(R.string.ok)
                    .show()
            }
        }
    }

    private fun renderXrefText() {
        val xrefEntry = xrefEntry ?: return

        val sb = SpannableStringBuilder()
        sb.append(VerseRenderer.XREF_MARK)
        sb.append(" ")
        var linkPos = 0

        FormattedTextRenderer.render(xrefEntry.content, appendToThis = sb, tagListener = object : FormattedTextRenderer.TagListener {
            override fun onTag(tag: String, buffer: Spannable, start: Int, end: Int) {
                val thisLinkPos = linkPos++
                if (tag.startsWith("t")) { // the only supported tag at the moment
                    val encodedTarget = tag.substring(1)
                    if (thisLinkPos == displayedLinkPos || displayedLinkPos == -1 && thisLinkPos == 0) { // just make it bold, because this is the currently displayed link
                        buffer.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                        if (displayedLinkPos == -1) {
                            showVerses(0, encodedTarget)
                        }
                    } else {
                        buffer.setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                showVerses(thisLinkPos, encodedTarget)
                            }
                        }, start, end, 0)
                    }
                }
            }
        })

        applyTextAppearance(tXrefText, textSizeMult)
        tXrefText.text = sb
    }

    fun showVerses(linkPos: Int, encodedTarget: String) {
        displayedLinkPos = linkPos
        val ranges = TargetDecoder.decode(encodedTarget)
        if (BuildConfig.DEBUG) {
            AppLog.d(TAG, "linkPos $linkPos target=$encodedTarget ranges=$ranges")
        }

        val displayedVerseTexts = mutableListOf<String>()
        val displayedVerseNumberTexts = mutableListOf<String>()
        val verse_count = sourceVersion.loadVersesByAriRanges(ranges, displayedRealAris, displayedVerseTexts)
        if (verse_count > 0) { // set up verse number texts
            for (i in 0 until verse_count) {
                val ari = displayedRealAris[i]
                displayedVerseNumberTexts.add(Ari.toChapter(ari).toString() + ":" + Ari.toVerse(ari))
            }
            val firstAri = displayedRealAris[0]
            val notAvailableText = getString(R.string.generic_verse_not_available_in_this_version)

            versesController.versesDataModel = VersesDataModel(
                ari_bc_ = Ari.toBookChapter(firstAri),
                verses_ = XrefDialogVerses(notAvailableText, displayedVerseTexts, displayedVerseNumberTexts),
                version_ = sourceVersion,
                versionId_ = sourceVersionId
            )
        }
        renderXrefText()
    }

    private val selectedVersesListener = object : VersesController.SelectedVersesListener() {
        override fun onVerseSingleClick(verse_1: Int) {
            verseSelectedListener(arif_source, displayedRealAris[verse_1 - 1])
        }
    }

    companion object {
        /**
         * Call [init] immediately after calling this method.
         */
        fun newInstance(arif: Int) = XrefDialog().apply {
            arguments = bundleOf(EXTRA_arif to arif)
        }
    }
}
