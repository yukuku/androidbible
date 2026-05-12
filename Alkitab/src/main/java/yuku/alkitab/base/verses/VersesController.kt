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
     * This is different from the other [scrollToVerse] in that if the requested
     * verse has a pericope header, this will scroll to the verse, ignoring the pericope header.
     */
    fun scrollToVerse(verse_1: Int, prop: Float)

    /**
     * Scrolls to the pericope header above [verse_1] (if any), positioning it
     * so that [prop] of its height has been scrolled past the top edge.
     *
     * If this version has no pericope above [verse_1], the verse itself is
     * snapped to the top (the source's within-pericope [prop] is intentionally
     * dropped in that case — applying it to a verse would scroll past content
     * the source was still showing as a header).
     *
     * Used by the split view to mirror the source pane when its top visible
     * item is a pericope header.
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

    /**
     * Reserves [pxBottom] extra pixels at the bottom of the underlying
     * verse list view so the last verses are reachable while the audio bar
     * overlays the screen. Combined with `clipToPadding="false"` on the
     * RecyclerView, the padding region remains scrollable so verses pass
     * under the bar (YouTube-Music-style mini-player feel) instead of being
     * cut off behind it.
     *
     * Pass `0` to clear the inset when the bar slides out.
     */
    fun setAudioBarBottomInset(pxBottom: Int)
}
