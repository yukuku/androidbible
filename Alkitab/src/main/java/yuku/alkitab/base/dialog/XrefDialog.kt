package yuku.alkitab.base.dialog

import android.graphics.Typeface
import android.os.Bundle
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
import yuku.alkitab.base.S
import yuku.alkitab.base.dialog.base.BaseDialog
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Appearances.applyTextAppearance
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.verses.VersesAttributes
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.base.verses.VersesControllerImpl
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.base.verses.VersesListeners
import yuku.alkitab.base.verses.VersesUiModel
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.model.XrefEntry
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import java.util.Locale
import kotlin.properties.Delegates.notNull

private const val TAG = "XrefDialog"
private const val EXTRA_arif = "arif"

class XrefDialog : BaseDialog() {
    private var arif_source by notNull<Int>()
    private var xrefEntry: XrefEntry? = null
    private lateinit var verseSelectedListener: (arif_source: Int, ari_target: Int) -> Unit

    private lateinit var tXrefText: TextView
    private lateinit var versesController: VersesController

    private lateinit var sourceVersion: Version
    private lateinit var sourceVersionId: String
    private var textSizeMult by notNull<Float>()

    private var displayedLinkPos = -1 // -1 indicates that we should auto-select the first link
    private val displayedVerseTexts = mutableListOf<String>()
    private val displayedVerseNumberTexts = mutableListOf<String>()
    private val displayedRealAris = IntArrayList()

    /**
     * This must be called after [newInstance].
     */
    fun init(sourceVersion: Version, sourceVersionId: String, verseSelectedListener: (arif_source: Int, ari_target: Int) -> Unit) {
        this.sourceVersion = sourceVersion
        this.sourceVersionId = sourceVersionId
        this.textSizeMult = S.getDb().getPerVersionSettings(sourceVersionId).fontSizeMultiplier
        this.verseSelectedListener = verseSelectedListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_TITLE, 0)
        this.arif_source = requireArguments().getInt(EXTRA_arif)
        this.xrefEntry = sourceVersion.getXrefEntry(arif_source)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.dialog_xref, container, false).apply {
            tXrefText = findViewById(R.id.tXrefText)
            setBackgroundColor(S.applied().backgroundColor)

            versesController = VersesControllerImpl(
                findViewById(R.id.lsView),
                "xref",
                VersesDataModel.EMPTY,
                VersesUiModel.EMPTY.copy(
                    verseSelectionMode = VersesController.VerseSelectionMode.singleClick
                ),
                VersesListeners.EMPTY.copy(
                    selectedVersesListener = selectedVersesListener
                )
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

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (xrefEntry == null) {
            dismiss()
        }
    }

    private fun renderXrefText() {
        val xrefEntry = xrefEntry ?: return

        val sb = SpannableStringBuilder()
        sb.append(VerseRenderer.XREF_MARK)
        sb.append(" ")
        var linkPos = 0

        findTags(xrefEntry.content, object : FindTagsListener {
            override fun onTaggedText(tag: String, start: Int, end: Int) {
                val thisLinkPos = linkPos
                linkPos++
                val sb_len = sb.length
                sb.append(xrefEntry.content, start, end)
                if (tag.startsWith("t")) { // the only supported tag at the moment
                    val encodedTarget = tag.substring(1)
                    if (thisLinkPos == displayedLinkPos || displayedLinkPos == -1 && thisLinkPos == 0) { // just make it bold, because this is the currently displayed link
                        sb.setSpan(StyleSpan(Typeface.BOLD), sb_len, sb.length, 0)
                        if (displayedLinkPos == -1) {
                            showVerses(0, encodedTarget)
                        }
                    } else {
                        sb.setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                showVerses(thisLinkPos, encodedTarget)
                            }
                        }, sb_len, sb.length, 0)
                    }
                }
            }

            override fun onPlainText(start: Int, end: Int) {
                sb.append(xrefEntry.content, start, end)
            }
        })

        applyTextAppearance(tXrefText, textSizeMult)
        tXrefText.text = sb
    }

    fun showVerses(linkPos: Int, encodedTarget: String) {
        displayedLinkPos = linkPos
        val ranges = decodeTarget(encodedTarget)
        if (BuildConfig.DEBUG) {
            AppLog.d(TAG, "linkPos $linkPos target=$encodedTarget ranges=$ranges")
        }
        val verse_count = sourceVersion.loadVersesByAriRanges(ranges, displayedRealAris, displayedVerseTexts)
        if (verse_count > 0) { // set up verse number texts
            for (i in 0 until verse_count) {
                val ari = displayedRealAris[i]
                displayedVerseNumberTexts.add(Ari.toChapter(ari).toString() + ":" + Ari.toVerse(ari))
            }
            val firstAri = displayedRealAris[0]
            val verses = XrefDialogVerses(requireContext(), displayedVerseTexts, displayedVerseNumberTexts)

            versesController.versesDataModel = VersesDataModel(Ari.toBookChapter(firstAri), verses, 0, IntArray(0), emptyArray(), sourceVersion, sourceVersionId, VersesAttributes.createEmpty(verses.verseCount))
        }
        renderXrefText()
    }

    private fun decodeTarget(encodedTarget: String): IntArrayList {
        return TargetDecoder.decode(encodedTarget)
    }

    private val selectedVersesListener = object : VersesController.SelectedVersesListener() {
        override fun onVerseSingleClick(verse_1: Int) {
            verseSelectedListener(arif_source, displayedRealAris[verse_1 - 1])
        }
    }

    private interface FindTagsListener {
        fun onPlainText(start: Int, end: Int)
        fun onTaggedText(tag: String, start: Int, end: Int)
    }

    // look for "<@" "@>" "@/" tags
    private fun findTags(s: String, listener: FindTagsListener) {
        var pos = 0
        while (true) {
            val p = s.indexOf("@<", pos)
            if (p == -1) break
            listener.onPlainText(pos, p)
            val q = s.indexOf("@>", p + 2)
            if (q == -1) break
            val r = s.indexOf("@/", q + 2)
            if (r == -1) break
            listener.onTaggedText(s.substring(p + 2, q), q + 2, r)
            pos = r + 2
        }
        listener.onPlainText(pos, s.length)
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
