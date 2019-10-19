package yuku.alkitab.base.verses

import yuku.alkitab.base.widget.VerseInlineLinkSpan
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

