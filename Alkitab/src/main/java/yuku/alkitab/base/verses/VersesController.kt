package yuku.alkitab.base.verses

import android.graphics.Rect
import android.graphics.drawable.Drawable
import yuku.alkitab.model.Version
import yuku.alkitab.util.IntArrayList

interface VersesController {
    enum class VerseSelectionMode {
        none,
        multiple,
        singleClick
    }

    abstract class SelectedVersesListener {
        open fun onSomeVersesSelected(verses_1: IntArrayList) {}

        open fun onNoVersesSelected() {}

        open fun onVerseSingleClick(verse_1: Int) {}
    }

    abstract class AttributeListener {
        open fun onBookmarkAttributeClick(version: Version, versionId: String, ari: Int) {}

        open fun onNoteAttributeClick(version: Version, versionId: String, ari: Int) {}

        open fun onProgressMarkAttributeClick(version: Version, versionId: String, preset_id: Int) {}

        open fun onHasMapsAttributeClick(version: Version, versionId: String, ari: Int) {}
    }

    abstract class VerseScrollListener {
        /**
         * Emitted while the user scrolls. [isPericope] indicates whether
         * the sender's anchor was a pericope header (above [verse_1]) or
         * [verse_1] itself.
         *
         * [prop] is in `[0, 1]` and represents the fraction of the sender's
         * anchor extent already scrolled past `paddingTop`:
         *  - When [isPericope] is `false`: fraction of the verse's height.
         *  - When [isPericope] is `true`: fraction of the COMBINED height
         *    of the contiguous pericope-header block above [verse_1] (one
         *    or more headers treated as a single anchor unit, so verse
         *    panes with differing pericope counts/heights stay aligned).
         *
         * The receiver multiplies [prop] by its OWN matching item or block
         * height, so heights may differ freely between panes. A pane that
         * lacks a pericope block above [verse_1] treats it as zero-height
         * and simply pins the verse top to `paddingTop` while the sender
         * scrolls through its block.
         */
        open fun onVerseScroll(isPericope: Boolean, verse_1: Int, prop: Float) {}

        open fun onScrollToTop() {}
    }

    abstract class PinDropListener {
        open fun onPinDropped(presetId: Int, ari: Int) {}
    }

    sealed class PressResult {
        object Left : PressResult()
        object Right : PressResult()
        class Consumed(val targetVerse_1: Int) : PressResult()
        object Nop : PressResult()
    }

    // # field ctor

    /**
     * Name of this [VersesController] for debugging.
     */
    val name: String

    var versesDataModel: VersesDataModel
    var versesUiModel: VersesUiModel
    var versesListeners: VersesListeners

    fun uncheckAllVerses(callSelectedVersesListener: Boolean)
    fun checkVerses(verses_1: IntArrayList, callSelectedVersesListener: Boolean)

    /**
     * Returns a list of checked verse_1 in ascending order.
     * Old name: getSelectedVerses_1
     */
    fun getCheckedVerses_1(): IntArrayList

    fun scrollToTop()
    /**
     * This is different from the other [scrollToVerse] in that if the requested
     * verse has a pericope header, this will scroll to the top of the pericope header,
     * not to the top of the verse.
     */
    fun scrollToVerse(verse_1: Int)
    /**
     * Scrolls so that this pane's [verse_1] has had `prop * (this pane's
     * verse height)` pixels scrolled past the view's `paddingTop`. Used by
     * the split view when the source pane's first visible item is the verse
     * itself.
     */
    fun scrollToVerse(verse_1: Int, prop: Float)

    /**
     * Scrolls to the contiguous block of pericope headers above [verse_1]
     * (one or more, treated as a single anchor unit), positioning it so
     * that [prop] of THIS pane's combined block height has been scrolled
     * past `paddingTop`. If this pane has no pericope above [verse_1] the
     * verse itself is snapped to `paddingTop` (equivalent to a zero-height
     * block), so the receiver waits at the verse top while the source
     * scrolls through its own block.
     */
    fun scrollToPericope(verse_1: Int, prop: Float)

    /**
     * Returns 0 if the scroll position can't be determined (e.g. the view has 0 height).
     * Old name: getVerseBasedOnScroll
     */
    fun getVerse_1BasedOnScroll(): Int

    fun pageDown(): PressResult
    fun pageUp(): PressResult
    fun verseDown(): PressResult
    fun verseUp(): PressResult

    fun setViewVisibility(visibility: Int)
    fun setViewPadding(padding: Rect)

    /**
     * Extra vertical padding from window insets, kept separate from (and
     * added to) [setViewPadding]'s preference-derived padding, so the list
     * draws edge-to-edge behind the system bars and display cutout while the
     * scrolled-to-edge verses stay inside the safe area.
     */
    fun setViewVerticalInsets(topInset: Int, bottomInset: Int)
    fun setViewScrollbarThumb(thumb: Drawable)

    /**
     * Set the layout params of the view that is represented by this controller.
     */
    fun setViewLayoutSize(width: Int, height: Int)

    fun callAttentionForVerse(verse_1: Int)

    fun setEmptyMessage(message: CharSequence?, textColor: Int)

    /**
     * Sets the audio-highlight overlay on the verse with the given 1-based
     * number, clearing any previous highlight. Pass `0` to clear all
     * highlights without setting a new one.
     *
     * The [color] argb is the pre-resolved overlay color (typically computed
     * by `AudioHighlightColor.pickHighlightColor`) — it's the controller's
     * job to recompute when the reading theme changes.
     *
     * Implementations also smooth-scroll the highlighted verse into the upper
     * third of the viewport so the row remains visible during long passages
     * without snapping the page each time the verse changes.
     */
    fun setAudioHighlight(verse_1: Int, color: Int)
}
