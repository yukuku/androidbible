package yuku.alkitab.base.verses

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.SparseIntArray
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import yuku.alkitab.base.App
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.verses.VersesDataModel.ItemType
import yuku.alkitab.base.widget.AriParallelClickData
import yuku.alkitab.base.widget.FormattedTextRenderer
import yuku.alkitab.base.widget.LeftDrawer.PROGRESS_MARK_DRAG_MIME_TYPE
import yuku.alkitab.base.widget.ParallelClickData
import yuku.alkitab.base.widget.ReferenceParallelClickData
import yuku.alkitab.util.IntArrayList

private const val TAG = "VersesComposeCtl"

private const val SCROLLBAR_FADE_DELAY_MS = 1000L
private const val SCROLLBAR_FADE_DURATION_MS = 400
private const val EMPTY_MESSAGE_TEXT_SIZE_DP = 14f

/**
 * Host view for the fully Compose-based verse list (the Verse (Compose)
 * experimental setting). A plain [AbstractComposeView] whose content is
 * whatever [VersesComposeControllerImpl] is currently attached; the controller
 * owns all list state so it survives this view's composition being disposed
 * and recreated (e.g. while the reader relocates its toolbar).
 */
class VersesComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {

    internal var controller by mutableStateOf<VersesComposeControllerImpl?>(null)

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    @Composable
    override fun Content() {
        controller?.ListContent()
    }
}

/**
 * [VersesController] implementation backed by a Compose [LazyColumn] instead
 * of a RecyclerView. Selected when the Verse (Compose) experimental setting is
 * enabled; the RecyclerView-based [VersesControllerImpl] remains the default.
 *
 * The full controller contract is honored so `IsiActivity`, the split-view
 * manager, gestures, and audio playback drive both implementations
 * identically:
 * - scroll positions are anchored the same way the RecyclerView port anchors
 *   them (a verse "at the top" sits at the view's top edge, ignoring the
 *   scroll-past content padding, except for the very first item);
 * - user scrolls emit [VersesController.VerseScrollListener] callbacks with
 *   the same verse/pericope-block prop semantics, while controller-initiated
 *   snap scrolls stay silent (mirroring RecyclerView, where only drags,
 *   flings, and smooth scrolls produce scroll callbacks);
 * - offscreen item heights, which RecyclerView obtains by measuring a
 *   throwaway holder, are read from a cache of previously laid-out sizes, or
 *   obtained by snapping the item into view first and reading its laid-out
 *   size before applying the final offset in the same frame.
 */
class VersesComposeControllerImpl(
    private val composeHost: VersesComposeView,
    override val name: String,
    versesDataModel: VersesDataModel = VersesDataModel.EMPTY,
    versesUiModel: VersesUiModel = VersesUiModel.EMPTY,
    versesListeners: VersesListeners = VersesListeners.EMPTY,
) : VersesController {

    private class DataWithVersion(val vn: Int, val data: VersesDataModel)

    private data class AttentionUi(val start: Long, val verses_1: Set<Int>) {
        companion object {
            val NONE = AttentionUi(0L, emptySet())
        }
    }

    internal val listState = LazyListState()

    private val dataVersionNumber = AtomicInteger()
    private var dataWithVersion by mutableStateOf(DataWithVersion(0, versesDataModel))
    private var uiState by mutableStateOf(versesUiModel)
    private var listenersState by mutableStateOf(versesListeners)

    private var checkedPositionsState by mutableStateOf(emptySet<Int>())
    private var attentionState by mutableStateOf(AttentionUi.NONE)

    private var audioHighlightVerse1 by mutableIntStateOf(0)
    private var audioHighlightColorState by mutableIntStateOf(0)

    private val basePadding = Rect()
    private var topInset = 0
    private var bottomInset = 0
    private var paddingLeftPx by mutableIntStateOf(0)
    private var paddingTopPx by mutableIntStateOf(0)
    private var paddingRightPx by mutableIntStateOf(0)
    private var paddingBottomPx by mutableIntStateOf(0)

    private var emptyMessageState by mutableStateOf<CharSequence?>(null)
    // Matches the default color of EmptyableRecyclerView's Paint until
    // setEmptyMessage supplies one.
    private var emptyMessageColorState by mutableIntStateOf(0xff000000.toInt())
    private var scrollbarThumbState by mutableStateOf<Drawable?>(null)

    /**
     * The data version most recently reflected by a composition. Scroll
     * commands wait on this before touching [listState], so an index computed
     * against fresh data is never applied while the list still shows the
     * previous chapter (the LazyColumn equivalent of RecyclerView's
     * pending-scroll-position-applied-on-next-layout behavior).
     */
    private val renderedDataVersion = mutableIntStateOf(0)

    /**
     * Non-zero while a controller-initiated snap scroll is running, so the
     * scroll-event emitter stays silent for it. Smooth scrolls (audio
     * highlight) intentionally do not set this: their RecyclerView counterpart
     * also emits scroll callbacks, which keeps the split panes following the
     * audio verse.
     */
    private var programmaticScrollDepth = 0

    /**
     * Laid-out item heights by position, filled as items pass through the
     * viewport. Only valid for [cacheDataVersion]; used to estimate offscreen
     * item heights where RecyclerView would measure a throwaway holder.
     */
    private val itemSizeCache = SparseIntArray()
    private var cacheDataVersion = -1

    init {
        composeHost.controller = this
    }

    override var versesDataModel: VersesDataModel
        get() = dataWithVersion.data
        set(value) {
            val vn = dataVersionNumber.incrementAndGet()
            dataWithVersion = DataWithVersion(vn, value)
            attentionState = AttentionUi.NONE
        }

    override var versesUiModel: VersesUiModel
        get() = uiState
        set(value) {
            uiState = value
        }

    override var versesListeners: VersesListeners
        get() = listenersState
        set(value) {
            listenersState = value
        }

    override fun uncheckAllVerses(callSelectedVersesListener: Boolean) {
        checkedPositionsState = emptySet()

        if (callSelectedVersesListener) {
            listenersState.selectedVersesListener.onNoVersesSelected()
        }
    }

    override fun checkVerses(verses_1: IntArrayList, callSelectedVersesListener: Boolean) {
        uncheckAllVerses(false)

        val data = versesDataModel
        val newChecked = mutableSetOf<Int>()
        var i = 0
        val len = verses_1.size()
        while (i < len) {
            val verse_1 = verses_1.get(i)
            val count = data.itemCount
            val pos = data.getPositionIgnoringPericopeFromVerse(verse_1)
            if (pos != -1 && pos < count) {
                newChecked += pos
            }
            i++
        }
        checkedPositionsState = newChecked

        if (callSelectedVersesListener) {
            if (newChecked.isNotEmpty()) {
                listenersState.selectedVersesListener.onSomeVersesSelected(getCheckedVerses_1())
            } else {
                listenersState.selectedVersesListener.onNoVersesSelected()
            }
        }
    }

    override fun getCheckedVerses_1(): IntArrayList {
        val data = versesDataModel
        val checkedVerses_1 = mutableSetOf<Int>()
        for (checkedPosition in checkedPositionsState) {
            val verse_1 = data.getVerse_1FromPosition(checkedPosition)
            if (verse_1 >= 1) {
                checkedVerses_1 += verse_1
            }
        }

        val res = IntArrayList(checkedVerses_1.size)
        for (verse_1 in checkedVerses_1.sorted()) {
            res.add(verse_1)
        }
        return res
    }

    private fun toggleChecked(position: Int) {
        val current = checkedPositionsState
        checkedPositionsState = if (position !in current) current + position else current - position

        if (checkedPositionsState.isNotEmpty()) {
            listenersState.selectedVersesListener.onSomeVersesSelected(getCheckedVerses_1())
        } else {
            listenersState.selectedVersesListener.onNoVersesSelected()
        }
    }

    // --- Scrolling ---

    /**
     * Runs a scroll operation once the composition has caught up with the data
     * version current at call time, dropping it if the data has changed again
     * by then (same guard as the RecyclerView implementation's posted scrolls).
     */
    private fun launchScroll(programmatic: Boolean = true, block: suspend (data: VersesDataModel) -> Unit) {
        val vn = dataVersionNumber.get()
        val scope = composeHost.findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        scope.launch {
            snapshotFlow { renderedDataVersion.intValue }.first { it >= vn }
            if (vn != dataVersionNumber.get()) return@launch
            if (programmatic) programmaticScrollDepth++
            try {
                block(dataWithVersion.data)
            } finally {
                if (programmatic) programmaticScrollDepth--
            }
        }
    }

    /**
     * Scroll offset that puts the item's top at the view's top edge (ignoring
     * the scroll-past top padding), except for the very first item which sits
     * below the padding — the same anchoring RecyclerView achieves with its
     * `paddingNegator`.
     */
    private fun snapOffsetPx(position: Int): Int = if (position == 0) 0 else paddingTopPx

    private fun visibleItemSizePx(position: Int): Int? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == position }?.size

    private fun findItemSizePx(position: Int): Int? {
        visibleItemSizePx(position)?.let { return it }
        if (cacheDataVersion == renderedDataVersion.intValue) {
            val cached = itemSizeCache.get(position, -1)
            if (cached >= 0) return cached
        }
        return null
    }

    private fun estimatedItemSizePx(position: Int): Int {
        findItemSizePx(position)?.let { return it }
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty()) return visible.sumOf { it.size } / visible.size
        return 0
    }

    override fun scrollToTop() {
        launchScroll { listState.scrollToItem(0) }
    }

    override fun scrollToVerse(verse_1: Int) {
        val position = versesDataModel.getPositionOfPericopeBeginningFromVerse(verse_1)

        if (position == -1) {
            AppLog.w(TAG, "could not find verse_1=$verse_1, weird!")
            return
        }

        launchScroll { data ->
            if (position >= data.itemCount) return@launchScroll
            listState.scrollToItem(position, snapOffsetPx(position))
        }
    }

    override fun scrollToVerse(verse_1: Int, prop: Float) {
        val position = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)
        if (position == -1) {
            AppLog.d(TAG, "could not find verse_1: $verse_1")
            return
        }

        scrollToPositionWithProp(position, prop)
    }

    override fun scrollToPericope(verse_1: Int, prop: Float) {
        val data = versesDataModel
        val blockStartPos = data.getPositionOfPericopeBeginningFromVerse(verse_1)
        if (blockStartPos == -1) {
            AppLog.d(TAG, "could not find pericope above verse_1: $verse_1")
            return
        }
        val versePos = data.getPositionIgnoringPericopeFromVerse(verse_1)
        if (blockStartPos == versePos) {
            // No pericope above the verse on this pane — treat as a
            // zero-height block: pin the verse top while the sender's
            // pericope scrolls.
            scrollToPositionWithProp(versePos, 0f)
            return
        }

        launchScroll { latest ->
            if (versePos >= latest.itemCount) return@launchScroll

            val snapBase = if (blockStartPos == 0) 0 else paddingTopPx

            var combinedHeight = 0
            var unknown = false
            for (p in blockStartPos until versePos) {
                val size = findItemSizePx(p)
                if (size == null) {
                    unknown = true
                    break
                }
                combinedHeight += size
            }
            if (unknown) {
                // Snap the block into view so its items get laid out, then
                // read the real sizes; the final snap below lands in the same
                // frame, so no intermediate position is ever rendered.
                listState.scrollToItem(blockStartPos, snapBase)
                combinedHeight = 0
                for (p in blockStartPos until versePos) {
                    combinedHeight += visibleItemSizePx(p) ?: 0
                }
            }

            listState.scrollToItem(blockStartPos, snapBase + (prop * combinedHeight).toInt())
        }
    }

    private fun scrollToPositionWithProp(position: Int, prop: Float) {
        launchScroll { data ->
            if (position >= data.itemCount) return@launchScroll

            var height = findItemSizePx(position)
            if (height == null) {
                listState.scrollToItem(position, paddingTopPx)
                height = visibleItemSizePx(position) ?: return@launchScroll
            }

            listState.scrollToItem(position, paddingTopPx + (prop * height).toInt())
        }
    }

    /**
     * Returns -1 if there is no visible child (e.g. when split is collapsed until the height is 0).
     */
    private fun getPositionBasedOnScroll(): Int {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return -1

        val top = first.offset + paddingTopPx
        if (top == 0) {
            return first.index
        }
        val bottom = top + first.size
        return if (bottom > 0) {
            first.index
        } else {
            first.index + 1
        }
    }

    override fun getVerse_1BasedOnScroll(): Int {
        return versesDataModel.getVerse_1FromPosition(getPositionBasedOnScroll())
    }

    override fun pageDown(): VersesController.PressResult {
        val info = listState.layoutInfo
        val oldPos = info.visibleItemsInfo.firstOrNull()?.index ?: -1
        var newPos = info.visibleItemsInfo.lastOrNull()?.index ?: -1

        if (oldPos == newPos && oldPos < versesDataModel.itemCount - 1) { // in case of very long item
            newPos = oldPos + 1
        }

        val targetPos = newPos
        if (targetPos >= 0) {
            launchScroll { data ->
                if (targetPos >= data.itemCount) return@launchScroll
                listState.scrollToItem(targetPos, snapOffsetPx(targetPos))
            }
        }

        return VersesController.PressResult.Consumed(versesDataModel.getVerse_1FromPosition(newPos))
    }

    override fun pageUp(): VersesController.PressResult {
        val info = listState.layoutInfo
        val oldPos = info.visibleItemsInfo.firstOrNull()?.index ?: -1
        val targetHeight = (info.viewportSize.height - paddingTopPx - paddingBottomPx).coerceAtLeast(0)

        var totalHeight = 0

        // consider how long the first child has been scrolled up
        val firstItem = info.visibleItemsInfo.firstOrNull()
        if (firstItem != null) {
            totalHeight += -(firstItem.offset + paddingTopPx)
        }

        var curPos = oldPos
        // try until totalHeight exceeds targetHeight
        while (true) {
            curPos--
            if (curPos < 0) {
                break
            }

            totalHeight += estimatedItemSizePx(curPos)

            if (totalHeight > targetHeight) {
                break
            }
        }

        var newPos = curPos + 1

        if (oldPos == newPos && oldPos > 0) { // move at least one
            newPos = oldPos - 1
        }

        val targetPos = newPos
        launchScroll { data ->
            if (targetPos >= data.itemCount) return@launchScroll
            listState.scrollToItem(targetPos, snapOffsetPx(targetPos))
        }

        return VersesController.PressResult.Consumed(versesDataModel.getVerse_1FromPosition(newPos))
    }

    override fun verseDown(): VersesController.PressResult {
        val oldVerse_1 = getVerse_1BasedOnScroll()

        val newVerse_1 = if (oldVerse_1 < versesDataModel.verses_.verseCount) {
            oldVerse_1 + 1
        } else {
            oldVerse_1
        }

        scrollToVerse(newVerse_1)
        return VersesController.PressResult.Consumed(newVerse_1)
    }

    override fun verseUp(): VersesController.PressResult {
        val oldVerse_1 = getVerse_1BasedOnScroll()

        val newVerse_1 = if (oldVerse_1 > 1) { // can still go prev
            oldVerse_1 - 1
        } else {
            oldVerse_1
        }

        scrollToVerse(newVerse_1)
        return VersesController.PressResult.Consumed(newVerse_1)
    }

    // --- View-level operations ---

    override fun setViewVisibility(visibility: Int) {
        composeHost.visibility = visibility
    }

    override fun setViewPadding(padding: Rect) {
        basePadding.set(padding)
        applyPadding()
    }

    override fun setViewVerticalInsets(topInset: Int, bottomInset: Int) {
        if (this.topInset == topInset && this.bottomInset == bottomInset) return
        this.topInset = topInset
        this.bottomInset = bottomInset
        applyPadding()
    }

    private fun applyPadding() {
        val newTop = basePadding.top + topInset
        val deltaTop = newTop - paddingTopPx

        paddingLeftPx = basePadding.left
        paddingTopPx = newTop
        paddingRightPx = basePadding.right
        paddingBottomPx = basePadding.bottom + bottomInset

        // A LazyColumn anchors its scroll position relative to the content
        // start, so a top-padding change would shift the verses on screen.
        // Counter-scroll by the delta to keep the first visible verse at the
        // same pixel position — except when the list rests at the very top,
        // where the first verse must follow the padding into the safe area.
        if (deltaTop != 0 && listState.canScrollBackward) {
            launchScroll { listState.scrollBy(deltaTop.toFloat()) }
        }
    }

    override fun setViewScrollbarThumb(thumb: Drawable) {
        scrollbarThumbState = thumb
    }

    override fun setViewLayoutSize(width: Int, height: Int) {
        composeHost.updateLayoutParams {
            this.width = width
            this.height = height
        }
    }

    override fun setEmptyMessage(message: CharSequence?, textColor: Int) {
        emptyMessageState = message
        emptyMessageColorState = textColor
    }

    // --- Attention and audio highlight ---

    override fun callAttentionForVerse(verse_1: Int) {
        val pos = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)
        if (pos == -1) return

        attentionState = AttentionUi(System.currentTimeMillis(), attentionState.verses_1 + verse_1)
    }

    override fun setAudioHighlight(verse_1: Int, color: Int) {
        // Fast-path: this method is called every 100ms during playback
        // (mirroring the service's position poll). Bailing out when nothing
        // actually changed avoids restarting the highlight scroll.
        if (audioHighlightVerse1 == verse_1 && audioHighlightColorState == color) return

        audioHighlightVerse1 = verse_1
        audioHighlightColorState = color
        if (verse_1 == 0) return

        val pos = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)
        if (pos == -1) return

        // Smooth-scroll the highlighted verse so its top sits at the upper 10%
        // of the viewport — keeps a thin slice of the previous verse visible
        // for context while leaving room for upcoming verses below. Skipped
        // when the row is already fully visible inside the viewport: yanking
        // the page when the verse is sitting right in front of the user is
        // more distracting than helpful.
        launchScroll(programmatic = false) { data ->
            if (pos >= data.itemCount) return@launchScroll

            val info = listState.layoutInfo
            val viewportHeight = info.viewportSize.height
            if (viewportHeight <= 0) return@launchScroll

            val targetTop = paddingTopPx + ((viewportHeight - paddingTopPx - paddingBottomPx) * 0.10f).toInt()

            val item = info.visibleItemsInfo.firstOrNull { it.index == pos }
            if (item != null) {
                val topVisual = item.offset + paddingTopPx
                val bottomVisual = topVisual + item.size
                if (topVisual >= paddingTopPx && bottomVisual <= viewportHeight - paddingBottomPx) return@launchScroll
                listState.animateScrollBy((topVisual - targetTop).toFloat())
            } else {
                listState.animateScrollToItem(pos, paddingTopPx)
                val landed = visibleItemSizePx(pos) ?: return@launchScroll
                // The item's top now sits at the view's top edge; nudge it
                // down to the 10% mark (bounded by its height so a very short
                // viewport can't scroll it back out).
                if (landed > 0) {
                    val topVisual = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pos }
                        ?.let { it.offset + paddingTopPx }
                        ?: return@launchScroll
                    val delta = topVisual - targetTop
                    if (delta != 0) listState.animateScrollBy(delta.toFloat())
                }
            }
        }
    }

    // --- Scroll event emission (split-pane sync) ---

    /**
     * Mirror of the RecyclerView scroll listener: reports the first visible
     * verse (or the pericope-header block above it, treated as one anchor
     * unit) and the fraction of its extent scrolled past the view's top edge.
     */
    private fun emitVerseScrollEvents() {
        val data = dataWithVersion.data
        val listeners = listenersState
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return

        var prop = 0f
        var position = -1
        var anchorHeight = 0

        val remaining = first.offset + first.size + paddingTopPx // visual bottom; padding top is ignored
        if (remaining >= 0) { // bottom of first child is lower than the top edge
            position = first.index
            anchorHeight = first.size
            prop = if (anchorHeight > 0) 1f - remaining.toFloat() / anchorHeight else 0f
        } else {
            info.visibleItemsInfo.getOrNull(1)?.let { second ->
                position = second.index
                anchorHeight = second.size
                prop = if (anchorHeight > 0) (-remaining).toFloat() / anchorHeight else 0f
            }
        }

        if (position < 0) return

        val verseOrPericope = data.getVerseOrPericopeFromPosition(position)
        if (verseOrPericope > 0) {
            listeners.verseScrollListener.onVerseScroll(false, verseOrPericope, prop)
        } else {
            // Contiguous pericope headers above a verse are reported
            // as a single anchor unit so panes whose versions have
            // different pericope counts/heights stay aligned across
            // the whole block instead of bumping at each header.
            var blockStartPos = position
            while (blockStartPos > 0 &&
                data.getItemViewType(blockStartPos - 1) == ItemType.pericope
            ) {
                blockStartPos--
            }
            val itemCount = data.itemCount
            var versePos = position + 1
            while (versePos < itemCount &&
                data.getItemViewType(versePos) == ItemType.pericope
            ) {
                versePos++
            }
            if (versePos >= itemCount) return
            val nextVerse_1 = data.getVerse_1FromPosition(versePos)
            if (nextVerse_1 > 0) {
                var heightsBefore = 0
                for (p in blockStartPos until position) {
                    heightsBefore += findItemSizePx(p) ?: 0
                }
                var combinedHeight = heightsBefore + anchorHeight
                for (p in position + 1 until versePos) {
                    combinedHeight += findItemSizePx(p) ?: 0
                }

                val scrolledOfAnchorPx = prop * anchorHeight
                val combinedScrolledPx = heightsBefore + scrolledOfAnchorPx
                val propCombined = if (combinedHeight > 0) combinedScrolledPx / combinedHeight else 0f

                listeners.verseScrollListener.onVerseScroll(true, nextVerse_1, propCombined)
            }
        }

        if (first.index == 0 && first.offset == 0) {
            listeners.verseScrollListener.onScrollToTop()
        }
    }

    // --- Composition ---

    @Composable
    internal fun ListContent() {
        val dataW = dataWithVersion
        val data = dataW.data
        val ui = uiState
        val listeners = listenersState

        SideEffect {
            renderedDataVersion.intValue = dataW.vn
        }

        // Record laid-out item sizes for offscreen-height estimates. Keyed by
        // the rendered data version so sizes from a previous chapter never
        // leak into the new one.
        LaunchedEffect(Unit) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collect { visible ->
                val vn = renderedDataVersion.intValue
                if (cacheDataVersion != vn) {
                    itemSizeCache.clear()
                    cacheDataVersion = vn
                }
                for (item in visible) {
                    itemSizeCache.put(item.index, item.size)
                }
            }
        }

        // Emit verse-scroll callbacks for user-driven scrolls (drags, flings,
        // and smooth scrolls), skipping controller-initiated snaps.
        LaunchedEffect(Unit) {
            snapshotFlow {
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                if (first == null) Long.MIN_VALUE else (first.index.toLong() shl 32) or (first.offset.toLong() and 0xffffffffL)
            }
                .drop(1)
                .collect {
                    if (!listState.isScrollInProgress) return@collect
                    if (programmaticScrollDepth > 0) return@collect
                    emitVerseScrollEvents()
                }
        }

        // Strip fontScale from the density: verse text uses dp sizing (not
        // sp), so the system font scale must not multiply on top.
        val baseDensity = LocalDensity.current
        val unscaledDensity = remember(baseDensity.density) {
            Density(density = baseDensity.density, fontScale = 1f)
        }

        CompositionLocalProvider(LocalDensity provides unscaledDensity) {
            Box(modifier = Modifier.fillMaxSize()) {
                val contentPadding = with(unscaledDensity) {
                    PaddingValues.Absolute(
                        left = paddingLeftPx.toDp(),
                        top = paddingTopPx.toDp(),
                        right = paddingRightPx.toDp(),
                        bottom = paddingBottomPx.toDp(),
                    )
                }

                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .verseListScrollbar(listState, scrollbarThumbState),
                ) {
                    items(
                        count = data.itemCount,
                        key = { position -> itemKey(data, position) },
                        contentType = { position -> data.getItemViewType(position) },
                    ) { position ->
                        when (data.getItemViewType(position)) {
                            ItemType.verseText -> VerseRow(data, ui, listeners, position)
                            ItemType.pericope -> PericopeHeaderComposeItem(data, ui, listeners, position, data.getPericopeIndex(position))
                        }
                    }
                }

                val emptyMessage = emptyMessageState
                if (emptyMessage != null) {
                    BasicText(
                        text = emptyMessage.toString(),
                        style = TextStyle(
                            color = Color(emptyMessageColorState),
                            fontSize = EMPTY_MESSAGE_TEXT_SIZE_DP.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun VerseRow(data: VersesDataModel, ui: VersesUiModel, listeners: VersesListeners, position: Int) {
        val index = data.getVerse_0(position)
        val verse_1 = index + 1
        val checked = position in checkedPositionsState
        val context = LocalContext.current

        val state = remember(data, ui, listeners, position, checked) {
            buildVerseItemComposeState(
                context = context,
                data = data,
                ui = ui,
                listeners = listeners,
                index = index,
                checked = checked,
                currentPosition = { position },
                toggleChecked = { pos -> toggleChecked(pos) },
                inlineLinkViewProvider = { composeHost },
            )
        }

        val collapsed = data.verses_.getVerse(index).isEmpty() && !state.attribute.isShowingSomething

        val attention = attentionState
        val attentionStart = if (attention.start != 0L && verse_1 in attention.verses_1) attention.start else 0L
        val audioColor = if (verse_1 == audioHighlightVerse1) audioHighlightColorState else 0

        val dragHover = remember { mutableStateOf(false) }
        val dropTarget = remember(state) {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) {
                    dragHover.value = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    dragHover.value = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    dragHover.value = false
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    // Refuse the drop if the payload is missing or non-numeric.
                    val presetId = event.toAndroidDragEvent().clipData
                        ?.getItemAt(0)?.text?.toString()?.toIntOrNull()
                        ?: return false
                    state.onPinDropped(presetId)
                    return true
                }
            }
        }

        val description = remember(state) { verseItemContentDescription(context, state).toString() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                // TalkBack reads the row as one unit (verse number, text, and
                // attribute summaries), matching the View implementations, and
                // can activate it like the legacy row's View click listener.
                .clearAndSetSemantics {
                    contentDescription = description
                    onClick {
                        state.onClick()
                        true
                    }
                }
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event -> event.mimeTypes().contains(PROGRESS_MARK_DRAG_MIME_TYPE) },
                    target = dropTarget,
                )
        ) {
            VerseItemComposeContent(
                state = state,
                checked = checked,
                collapsed = collapsed,
                audioHighlightColor = audioColor,
                attentionStart = attentionStart,
                dragHover = dragHover.value,
                // The overlay stops drawing by itself once the flash has
                // decayed; there is no per-row state to reset here.
                onAttentionDone = {},
            )
        }
    }
}

/**
 * Stable per-position key, using the same scheme as the RecyclerView
 * adapter's stable ids: verses are `verse_1 * 1000`, pericope headers sit
 * just below the following verse's key.
 */
private fun itemKey(data: VersesDataModel, position: Int): Long {
    return when (data.getItemViewType(position)) {
        ItemType.verseText -> data.getVerse_1FromPosition(position) * 1000L
        ItemType.pericope -> {
            when (val locateResult = data.locateVerse_1FromPosition(position)) {
                LocateResult.EMPTY -> 1000000L + position
                else -> locateResult.verse_1 * 1000L - locateResult.distanceToNextVerse
            }
        }
    }
}

/**
 * Compose port of the pericope header row: centered bold title, then the
 * optional parallels line with tappable references.
 */
@Composable
internal fun PericopeHeaderComposeItem(
    data: VersesDataModel,
    ui: VersesUiModel,
    listeners: VersesListeners,
    position: Int,
    index: Int,
) {
    val applied = App.services.uiDimensions.applied()
    val pericopeBlock = data.pericopeBlocks_[index]

    val baseDensity = LocalDensity.current
    val unscaledDensity = remember(baseDensity.density) {
        Density(density = baseDensity.density, fontScale = 1f)
    }

    CompositionLocalProvider(LocalDensity provides unscaledDensity) {
        // turn off top padding if the position == 0 OR before this is also a pericope title
        val paddingTopPx = if (position == 0 || data.getItemViewType(position - 1) == ItemType.pericope) {
            0
        } else {
            applied.pericopeSpacingTop
        }
        val paddingBottomPx = applied.pericopeSpacingBottom

        val fontFamily = remember(applied.fontFace) { composeFontFamilyFor(applied.fontFace) }
        val titleSizeDp = applied.fontSize2dp * ui.textSizeMult
        val titleLineMetrics = remember(applied.fontFace, titleSizeDp, applied.lineSpacingMult, unscaledDensity.density) {
            computeLineMetrics(applied.fontFace, titleSizeDp, android.graphics.Typeface.BOLD, applied.lineSpacingMult, unscaledDensity.density)
        }

        val titleText = remember(pericopeBlock.title) {
            spannedToAnnotatedString(FormattedTextRenderer.render(pericopeBlock.title))
        }

        Column(
            modifier = with(unscaledDensity) {
                Modifier
                    .fillMaxWidth()
                    .padding(top = paddingTopPx.toDp(), bottom = paddingBottomPx.toDp())
            }
        ) {
            BasicText(
                text = titleText,
                style = TextStyle(
                    color = Color(applied.fontColor),
                    fontSize = titleSizeDp.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    lineHeight = titleLineMetrics.lineHeightSp.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Proportional,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )

            if (pericopeBlock.parallels.isNotEmpty()) {
                val parallelsSizeDp = titleSizeDp * 0.8235294f
                val parallelsLineMetrics = remember(applied.fontFace, parallelsSizeDp, applied.lineSpacingMult, unscaledDensity.density) {
                    computeLineMetrics(applied.fontFace, parallelsSizeDp, android.graphics.Typeface.NORMAL, applied.lineSpacingMult, unscaledDensity.density)
                }
                val parallelsText = remember(pericopeBlock, listeners) {
                    buildParallelsAnnotatedString(pericopeBlock.parallels, listeners.parallelListener_)
                }

                BasicText(
                    text = parallelsText,
                    style = TextStyle(
                        color = Color(applied.fontColor),
                        fontSize = parallelsSizeDp.sp,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        lineHeight = parallelsLineMetrics.lineHeightSp.sp,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Proportional,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                )
            }
        }
    }
}

/**
 * Converts the simple formatted text produced by [FormattedTextRenderer]
 * (italic/bold [StyleSpan]s and line breaks) into an [AnnotatedString].
 */
internal fun spannedToAnnotatedString(spanned: Spanned): AnnotatedString = buildAnnotatedString {
    append(spanned.toString())
    for (span in spanned.getSpans(0, spanned.length, StyleSpan::class.java)) {
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        when (span.style) {
            android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            android.graphics.Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
        }
    }
}

/**
 * Builds the "(Matt. 1:1; Mark 1:2; ...)" parallels line with each reference
 * tappable, mirroring the span-based rendering of the RecyclerView pericope
 * holder including its forced line breaks for certain parallel counts.
 */
internal fun buildParallelsAnnotatedString(
    parallels: Array<String>,
    parallelListener: (ParallelClickData) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append("(")

    val total = parallels.size
    for (i in 0 until total) {
        val parallel = parallels[i]

        if (i > 0) {
            // force new line for certain parallel patterns
            if (total == 6 && i == 3 || total == 4 && i == 2 || total == 5 && i == 3) {
                append("; \n")
            } else {
                append("; ")
            }
        }

        appendParallelCompose(parallel, parallelListener)
    }
    append(")")
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendParallelCompose(
    parallel: String,
    parallelListener: (ParallelClickData) -> Unit,
) {
    fun linked(): Boolean {
        if (!parallel.startsWith("@")) {
            return false
        }

        // look for the end
        val targetEndPos = parallel.indexOf(' ', 1)
        if (targetEndPos == -1) {
            return false
        }

        val target = parallel.substring(1, targetEndPos)
        val ariRanges = TargetDecoder.decode(target)
        if (ariRanges.size() == 0) {
            return false
        }

        val display = parallel.substring(targetEndPos + 1)

        withLink(LinkAnnotation.Clickable("parallel") { parallelListener(AriParallelClickData(ariRanges.get(0))) }) {
            append(display)
        }
        return true
    }

    if (!linked()) {
        // fallback if the above code fails
        withLink(LinkAnnotation.Clickable("parallel") { parallelListener(ReferenceParallelClickData(parallel)) }) {
            append(parallel)
        }
    }
}

/**
 * Fading overlay scrollbar for the verse list, drawn with the same thumb
 * drawable the reader picks for the RecyclerView path (light/dark variants by
 * reading-background luminance). Thumb size and position are estimated from
 * the average laid-out item height — the standard approach for lazy lists,
 * where total content height is unknowable without laying everything out.
 */
@Composable
private fun Modifier.verseListScrollbar(listState: LazyListState, thumb: Drawable?): Modifier {
    if (thumb == null) return this

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { inProgress ->
            if (inProgress) {
                visible = true
            } else {
                delay(SCROLLBAR_FADE_DELAY_MS)
                visible = false
            }
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = SCROLLBAR_FADE_DURATION_MS),
        label = "verseListScrollbarAlpha",
    )

    return drawWithContent {
        drawContent()
        if (alpha <= 0.01f) return@drawWithContent

        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty()) return@drawWithContent
        val totalCount = info.totalItemsCount
        if (totalCount == 0) return@drawWithContent

        val avg = items.sumOf { it.size }.toFloat() / items.size
        if (avg <= 0f) return@drawWithContent

        val contentHeight = avg * totalCount + info.beforeContentPadding + info.afterContentPadding
        val viewportHeight = size.height
        if (contentHeight <= viewportHeight) return@drawWithContent

        val minThumbHeight = 32.dp.toPx()
        val thumbHeight = (viewportHeight * viewportHeight / contentHeight).coerceAtLeast(minThumbHeight)

        val first = items.first()
        val scrolledPx = (first.index * avg - first.offset).coerceAtLeast(0f)
        val maxScrollPx = (contentHeight - viewportHeight).coerceAtLeast(1f)
        val fraction = (scrolledPx / maxScrollPx).coerceIn(0f, 1f)
        val thumbTop = fraction * (viewportHeight - thumbHeight)

        val thumbWidth = if (thumb.intrinsicWidth > 0) thumb.intrinsicWidth.toFloat() else 4.dp.toPx()

        drawIntoCanvas { canvas ->
            thumb.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            thumb.setBounds(
                (size.width - thumbWidth).toInt(),
                thumbTop.toInt(),
                size.width.toInt(),
                (thumbTop + thumbHeight).toInt(),
            )
            thumb.draw(canvas.nativeCanvas)
        }
    }
}
