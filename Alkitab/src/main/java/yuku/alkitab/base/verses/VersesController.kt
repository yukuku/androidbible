package yuku.alkitab.base.verses

import android.graphics.Rect
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.model.Version
import yuku.alkitab.util.IntArrayList

interface VersesController {
    enum class VerseSelectionMode {
        none,
        multiple,
        singleClick
    }

    abstract class SelectedVersesListener {
        open fun onSomeVersesSelected(v: VersesController) {}

        open fun onNoVersesSelected(v: VersesController) {}

        open fun onVerseSingleClick(v: VersesController, verse_1: Int) {}
    }

    abstract class AttributeListener {
        open fun onBookmarkAttributeClick(version: Version, versionId: String, ari: Int) {}

        open fun onNoteAttributeClick(version: Version, versionId: String, ari: Int) {}

        open fun onProgressMarkAttributeClick(version: Version, versionId: String, preset_id: Int) {}

        open fun onHasMapsAttributeClick(version: Version, versionId: String, ari: Int) {}
    }

    abstract class OnVerseScrollListener {
        open fun onVerseScroll(v: VersesController, isPericope: Boolean, verse_1: Int, prop: Float) {}

        open fun onScrollToTop(v: VersesController) {}
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

    var versesDataModel: VersesDataModel

    fun uncheckAllVerses(callListener: Boolean)
    fun checkVerses(verses_1: IntArrayList, callListener: Boolean)
    /**
     * Old name: getSelectedVerses_1
     */
    fun getCheckedVerses_1(): IntArrayList

    fun reloadAttributeMap() {
        TODO() // make it load the whole data model
    }

    fun scrollToTop()
    /**
     * This is different from the other [scrollToVerse] in that if the requested
     * verse has a pericope header, this will scroll to the top of the pericope header,
     * not to the top of the verse.
     */
    fun scrollToVerse(verse_1: Int)
    /**
     * This is different from the other [scrollToVerse] in that if the requested
     * verse has a pericope header, this will scroll to the verse, ignoring the pericope header.
     */
    fun scrollToVerse(verse_1: Int, prop: Float)
    /**
     * Old name: getVerseBasedOnScroll
     */
    fun getVerse_1BasedOnScroll(): Int

    fun press(keyCode: Int): PressResult

    fun setPadding(padding: Rect)

    fun callAttentionForVerse(verse_1: Int)
}
