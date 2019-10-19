package yuku.alkitab.base.widget

import yuku.alkitab.base.util.Highlights
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version

interface VersesController {
    enum class VerseSelectionMode {
        none,
        multiple,
        singleClick
    }

    abstract class SelectedVersesListener {
        open fun onSomeVersesSelected(v: VersesViewController) {}

        open fun onNoVersesSelected(v: VersesViewController) {}

        open fun onVerseSingleClick(v: VersesViewController, verse_1: Int) {}
    }

    abstract class AttributeListener {
        open fun onBookmarkAttributeClick(version: Version, versionId: String, ari: Int) {}

        open fun onNoteAttributeClick(version: Version, versionId: String, ari: Int) {}

        open fun onProgressMarkAttributeClick(version: Version, versionId: String, preset_id: Int) {}

        open fun onHasMapsAttributeClick(version: Version, versionId: String, ari: Int) {}
    }

    abstract class OnVerseScrollListener {
        open fun onVerseScroll(v: VersesViewController, isPericope: Boolean, verse_1: Int, prop: Float) {}

        open fun onScrollToTop(v: VersesViewController) {}
    }

    enum class PressKind {
        left,
        right,
        consumed,
        nop
    }

    class PressResult(val kind: PressKind, val targetVerse_1: Int = 0) {
        companion object {
            val LEFT = PressResult(PressKind.left)
            val RIGHT = PressResult(PressKind.right)
            val NOP = PressResult(PressKind.nop)
        }
    }

    interface ParallelClickData

    // # field ctor

    /**
     * Name of this [VersesController] for debugging.
     */
    val name: String
    val verseSelectionMode: VerseSelectionMode
    val attributeListener: AttributeListener
    val listener: SelectedVersesListener
    val onVerseScrollListener: OnVerseScrollListener
    val parallelListener_: (ParallelClickData) -> Unit
    val inlineLinkSpanFactory_: VerseInlineLinkSpan.Factory

    val versesDataModel: VersesDataModel
}

class VersesDataModel(
    var ari_bc_: Int,
    var verses_: SingleChapterVerses?,
    var pericopeAris_: IntArray,
    var pericopeBlocks_: Array<PericopeBlock>,
    var version_: Version?,
    var versionId_: String?,
    var textSizeMult_: Float,

    var bookmarkCountMap_: IntArray,
    var noteCountMap_: IntArray,
    var highlightInfoMap_: Array<Highlights.Info>,
    var progressMarkBitsMap_: IntArray,
    var hasMapsMap_: BooleanArray
) {
    /**
     * For each element, if 0 or more, it refers to the 0-based verse number.
     * If negative, -1 is the index 0 of pericope, -2 (a) is index 1 (b) of pericope, etc.
     *
     * Convert a to b: b = -a-1;
     * Convert b to a: a = -b-1;
     */
    private val itemPointer_ by lazy {
        TODO()
    }

    init {

    }

    companion object {
        val EMPTY = VersesDataModel(
            ari_bc_ = 0,
            verses_ = null,
            pericopeAris_ = IntArray(0),
            pericopeBlocks_ = emptyArray(),
            version_ = null,
            versionId_ = null,
            textSizeMult_ = 1f,
            bookmarkCountMap_ = IntArray(0),
            noteCountMap_ = IntArray(0),
            highlightInfoMap_ = emptyArray(),
            progressMarkBitsMap_ = IntArray(0),
            hasMapsMap_ = BooleanArray(0)
        )
    }
}
