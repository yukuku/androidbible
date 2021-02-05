package yuku.alkitab.base.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import kotlin.properties.Delegates.notNull
import yuku.alkitab.base.S
import yuku.alkitab.base.dialog.VersesDialog.Companion.newCompareInstance
import yuku.alkitab.base.dialog.VersesDialog.Companion.newInstance
import yuku.alkitab.base.dialog.base.BaseDialog
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.util.Appearances.applyTextAppearance
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.base.verses.VersesControllerImpl
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.base.verses.VersesListeners
import yuku.alkitab.base.verses.VersesUiModel
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

private const val EXTRA_ariRanges = "ariRanges"
private const val EXTRA_ari = "ari"
private const val EXTRA_compareMode = "compareMode"

/**
 * Dialog that shows a list of verses. There are two modes:
 * "normal mode" that is created via [newInstance] to show a list of verses from a single version.
 * -- field ariRanges is used, compareMode==false.
 * "compare mode" that is created via [newCompareInstance] to show a list of different version of a verse.
 * -- field ari is used, compareMode==true.
 */
class VersesDialog : BaseDialog() {
    abstract class VersesDialogListener {
        open fun onVerseSelected(ari: Int) {}
        open fun onComparedVerseSelected(ari: Int, mversion: MVersion) {}
    }

    private var ari by notNull<Int>()
    private var compareMode by notNull<Boolean>()
    private lateinit var ariRanges: IntArrayList

    // data that will be passed when one verse is clicked
    private sealed class CallbackData {
        class WithMVersion(val mversion: MVersion) : CallbackData()
        class WithAri(val ari: Int) : CallbackData()
    }

    private lateinit var customCallbackDatas: Array<CallbackData>

    var listener: VersesDialogListener? = null
    var onDismissListener: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_TITLE, 0)

        // TODO appcompat 1.1.0: change to requireArguments()
        val arguments = requireNotNull(arguments)
        ariRanges = arguments.getParcelable(EXTRA_ariRanges) ?: IntArrayList(0)
        ari = arguments.getInt(EXTRA_ari)
        compareMode = arguments.getBoolean(EXTRA_compareMode)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val sourceVersion = S.activeVersion()
        val sourceVersionId = S.activeVersionId()
        val textSizeMult = S.getDb().getPerVersionSettings(sourceVersionId).fontSizeMultiplier

        val res = inflater.inflate(R.layout.dialog_verses, container, false)
        res.setBackgroundColor(S.applied().backgroundColor)
        val tReference = res.findViewById<TextView>(R.id.tReference)

        val versesController = VersesControllerImpl(
            res.findViewById(R.id.lsView),
            "verses",
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
            )
        )

        // build reference label
        val sb = StringBuilder()
        if (!compareMode) {
            var i = 0
            while (i < ariRanges.size()) {
                val ari_start = ariRanges[i]
                val ari_end = ariRanges[i + 1]
                if (sb.isNotEmpty()) {
                    sb.append("; ")
                }
                sb.append(sourceVersion.referenceRange(ari_start, ari_end))
                i += 2
            }
        } else {
            sb.append(sourceVersion.reference(ari))
        }
        applyTextAppearance(tReference, textSizeMult)
        tReference.text = sb

        versesController.versesDataModel = if (!compareMode) {
            val displayedAris = IntArrayList()
            val displayedVerseTexts = mutableListOf<String>()
            val verse_count = sourceVersion.loadVersesByAriRanges(ariRanges, displayedAris, displayedVerseTexts)
            customCallbackDatas = Array(verse_count) { i -> CallbackData.WithAri(displayedAris[i]) }
            val displayedVerseNumberTexts = List(verse_count) { i ->
                val ari = displayedAris[i]
                "${Ari.toChapter(ari)}:${Ari.toVerse(ari)}"
            }
            val firstAri = ariRanges[0]

            VersesDataModel(
                ari_bc_ = Ari.toBookChapter(firstAri),
                verses_ = VersesDialogNormalVerses(displayedVerseTexts, displayedVerseNumberTexts),
                version_ = sourceVersion,
                versionId_ = sourceVersionId
            )
        } else { // read each version and display it. First version must be the sourceVersion.
            val mversions = S.getAvailableVersions()
            // sort such that sourceVersion is first
            mversions.sortBy { if (it.versionId == sourceVersionId) -1 else 0 }

            val displayedVersion = arrayOfNulls<Version>(mversions.size)
            customCallbackDatas = Array(mversions.size) { i -> CallbackData.WithMVersion(mversions[i]) }

            VersesDataModel(
                ari_bc_ = Ari.toBookChapter(ari),
                verses_ = VersesDialogCompareVerses(requireContext(), ari, mversions, displayedVersion)
            )
        }

        return res
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener()
    }

    private val selectedVersesListener = object : VersesController.SelectedVersesListener() {
        override fun onVerseSingleClick(verse_1: Int) {
            val listener = listener ?: return
            val position = verse_1 - 1
            val callbackData = customCallbackDatas[position]

            if (!compareMode) {
                when (callbackData) {
                    is CallbackData.WithAri -> listener.onVerseSelected(callbackData.ari)
                }
            } else { // only if the verse is available in this version.
                when (callbackData) {
                    is CallbackData.WithMVersion -> {
                        if (callbackData.mversion.version?.loadVerseText(ari) != null) {
                            listener.onComparedVerseSelected(ari, callbackData.mversion)
                        }
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(ariRanges: IntArrayList) = VersesDialog().apply {
            arguments = bundleOf(
                EXTRA_ariRanges to ariRanges
            )
        }

        @JvmStatic
        fun newCompareInstance(ari: Int) = VersesDialog().apply {
            arguments = bundleOf(
                EXTRA_ari to ari,
                EXTRA_compareMode to true
            )
        }
    }
}
