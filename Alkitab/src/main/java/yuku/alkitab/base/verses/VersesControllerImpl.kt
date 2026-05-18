package yuku.alkitab.base.verses

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.atomic.AtomicInteger
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.settings.ExperimentalFlags
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.base.util.safeQuery
import yuku.alkitab.base.verses.VersesDataModel.ItemType
import yuku.alkitab.base.widget.AriParallelClickData
import yuku.alkitab.base.widget.DictionaryLinkInfo
import yuku.alkitab.base.widget.DictionaryLinkSpan
import yuku.alkitab.base.widget.FormattedTextRenderer
import yuku.alkitab.base.widget.ParallelClickData
import yuku.alkitab.base.widget.ParallelSpan
import yuku.alkitab.base.widget.PericopeHeaderItem
import yuku.alkitab.base.widget.ReferenceParallelClickData
import yuku.alkitab.base.widget.ScrollbarSetter.setVerticalThumb
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

private const val TAG = "VersesControllerImpl"

class VersesControllerImpl(
    private val rv: EmptyableRecyclerView,
    override val name: String,
    versesDataModel: VersesDataModel = VersesDataModel.EMPTY,
    versesUiModel: VersesUiModel = VersesUiModel.EMPTY,
    versesListeners: VersesListeners = VersesListeners.EMPTY,
) : VersesController {

    private val checkedPositions = mutableSetOf<Int>()
    private val attention = Attention()
    private val audioHighlight = AudioHighlight()

    private val basePadding = Rect()

    private val dataVersionNumber = AtomicInteger()

    private val layoutManager: LinearLayoutManager
    private val adapter: VersesAdapter

    init {
        val layoutManager = LinearLayoutManager(rv.context)
        this.layoutManager = layoutManager
        rv.layoutManager = layoutManager
        rv.addOnScrollListener(rvScrollListener)

        val adapter = VersesAdapter(
            attention = attention,
            audioHighlight = audioHighlight,
            isChecked = { position -> position in checkedPositions },
            toggleChecked = { position ->
                if (position !in checkedPositions) {
                    checkedPositions += position
                } else {
                    checkedPositions -= position
                }
                notifyItemChanged(position)

                if (checkedPositions.size > 0) {
                    listeners.selectedVersesListener.onSomeVersesSelected(getCheckedVerses_1())
                } else {
                    listeners.selectedVersesListener.onNoVersesSelected()
                }
            }
        )
        this.adapter = adapter
        rv.adapter = adapter
    }

    private val rvScrollListener
        get() = object : RecyclerView.OnScrollListener() {
            var scrollState = RecyclerView.SCROLL_STATE_IDLE

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                this.scrollState = newState
            }

            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(view, dx, dy)

                if (scrollState == RecyclerView.SCROLL_STATE_IDLE) return

                val firstVisibleItemPosition = layoutManager.getChildAt(0)
                    ?.let { layoutManager.getPosition(it) }
                    ?: layoutManager.findFirstVisibleItemPosition()
                if (firstVisibleItemPosition == RecyclerView.NO_POSITION) return
                val firstChild = layoutManager.findViewByPosition(firstVisibleItemPosition) ?: return

                var prop = 0f
                var position = -1
                var anchorHeight = 0

                val remaining = firstChild.bottom // padding top is ignored
                if (remaining >= 0) { // bottom of first child is lower than top padding
                    position = firstVisibleItemPosition
                    anchorHeight = firstChild.height
                    prop = if (anchorHeight > 0) 1f - remaining.toFloat() / anchorHeight else 0f
                } else {
                    layoutManager.findViewByPosition(firstVisibleItemPosition + 1)?.let { secondChild ->
                        position = firstVisibleItemPosition + 1
                        anchorHeight = secondChild.height
                        prop = if (anchorHeight > 0) (-remaining).toFloat() / anchorHeight else 0f
                    }
                }

                if (position < 0) return

                val verseOrPericope = versesDataModel.getVerseOrPericopeFromPosition(position)
                if (verseOrPericope > 0) {
                    versesListeners.verseScrollListener.onVerseScroll(false, verseOrPericope, prop)
                } else {
                    // Treat the entire contiguous pericope-header block above
                    // the next verse as a single anchor unit so panes with
                    // different pericope counts/heights still progress
                    // smoothly across the block. Walk the block boundaries
                    // from the current position via the cheap O(1)
                    // getItemViewType lookups rather than the data model's
                    // O(N) verse-search helpers, since this runs on every
                    // scroll frame.
                    var blockStartPos = position
                    while (blockStartPos > 0 &&
                        versesDataModel.getItemViewType(blockStartPos - 1) == ItemType.pericope
                    ) {
                        blockStartPos--
                    }
                    val itemCount = versesDataModel.itemCount
                    var versePos = position + 1
                    while (versePos < itemCount &&
                        versesDataModel.getItemViewType(versePos) == ItemType.pericope
                    ) {
                        versePos++
                    }
                    if (versePos >= itemCount) return
                    val nextVerse_1 = versesDataModel.getVerse_1FromPosition(versePos)
                    if (nextVerse_1 > 0) {
                        var heightsBefore = 0
                        for (p in blockStartPos until position) {
                            heightsBefore += layoutManager.findViewByPosition(p)?.height ?: getMeasuredItemHeight(p)
                        }
                        var combinedHeight = heightsBefore + anchorHeight
                        for (p in position + 1 until versePos) {
                            combinedHeight += layoutManager.findViewByPosition(p)?.height ?: getMeasuredItemHeight(p)
                        }

                        // `prop * anchorHeight` works for both the >=0 and <0
                        // remaining branches above; the older
                        // `anchorHeight - remaining` form was only correct in
                        // the >=0 branch and produced > anchorHeight in the
                        // other branch (when the previous item's bottom is
                        // already off-screen).
                        val scrolledOfAnchorPx = prop * anchorHeight
                        val combinedScrolledPx = heightsBefore + scrolledOfAnchorPx
                        val propCombined = if (combinedHeight > 0) combinedScrolledPx / combinedHeight else 0f

                        versesListeners.verseScrollListener.onVerseScroll(true, nextVerse_1, propCombined)
                    }
                }

                if (firstVisibleItemPosition == 0 && firstChild.top == view.paddingTop) {
                    versesListeners.verseScrollListener.onScrollToTop()
                }
            }
        }

    /**
     * Data for adapter: Verse data
     */
    override var versesDataModel = versesDataModel
        set(value) {
            field = value
            dataVersionNumber.incrementAndGet()
            attention.clear()
            render()
        }

    /**
     * Data for adapter: UI data
     */
    override var versesUiModel = versesUiModel
        set(value) {
            field = value
            render()
        }

    /**
     * Data for adapter: Callbacks
     */
    override var versesListeners = versesListeners
        set(value) {
            field = value
            render()
        }

    override fun uncheckAllVerses(callSelectedVersesListener: Boolean) {
        // Animate
        for (checkedPosition in checkedPositions) {
            adapter.notifyItemChanged(checkedPosition)
        }

        checkedPositions.clear()

        if (callSelectedVersesListener) {
            versesListeners.selectedVersesListener.onNoVersesSelected()
        }
    }

    override fun checkVerses(verses_1: IntArrayList, callSelectedVersesListener: Boolean) {
        uncheckAllVerses(false)

        var checked_count = 0
        var i = 0
        val len = verses_1.size()
        while (i < len) {
            val verse_1 = verses_1.get(i)
            val count = versesDataModel.itemCount
            val pos = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)
            if (pos != -1 && pos < count) {
                checkedPositions += pos
                checked_count++
            }
            i++
        }

        // Animate
        for (checkedPosition in checkedPositions) {
            adapter.notifyItemChanged(checkedPosition)
        }

        if (callSelectedVersesListener) {
            if (checked_count > 0) {
                versesListeners.selectedVersesListener.onSomeVersesSelected(getCheckedVerses_1())
            } else {
                versesListeners.selectedVersesListener.onNoVersesSelected()
            }
        }
    }

    override fun getCheckedVerses_1(): IntArrayList {
        val checkedVerses_1 = mutableSetOf<Int>()
        for (checkedPosition in checkedPositions) {
            val verse_1 = versesDataModel.getVerse_1FromPosition(checkedPosition)
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

    override fun scrollToTop() {
        rv.scrollToPosition(0)
    }

    override fun scrollToVerse(verse_1: Int) {
        val position = versesDataModel.getPositionOfPericopeBeginningFromVerse(verse_1)

        if (position == -1) {
            AppLog.w(TAG, "could not find verse_1=$verse_1, weird!")
        } else {
            val vn = dataVersionNumber.get()

            rv.post {
                // this may happen async from above, so check data version first
                if (vn != dataVersionNumber.get()) return@post

                // negate padding offset, unless this is the first verse
                val paddingNegator = if (position == 0) 0 else -rv.paddingTop

                layoutManager.scrollToPositionWithOffset(position, paddingNegator)
            }
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
        val blockStartPos = versesDataModel.getPositionOfPericopeBeginningFromVerse(verse_1)
        if (blockStartPos == -1) {
            AppLog.d(TAG, "could not find pericope above verse_1: $verse_1")
            return
        }
        val versePos = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)
        if (blockStartPos == versePos) {
            // No pericope above the verse on this pane → treat the block as
            // zero-height; snap the verse to view top while the source pane
            // scrolls through its own block.
            scrollToPositionWithProp(versePos, 0f)
            return
        }

        val vn = dataVersionNumber.get()
        rv.post(fun() {
            if (vn != dataVersionNumber.get()) return
            if (versePos >= versesDataModel.itemCount) return

            var combinedHeight = 0
            for (p in blockStartPos until versePos) {
                combinedHeight += layoutManager.findViewByPosition(p)?.height ?: getMeasuredItemHeight(p)
            }

            rv.stopScroll()
            val paddingNegator = if (blockStartPos == 0) 0 else -rv.paddingTop
            val offset = -(prop * combinedHeight).toInt() + paddingNegator
            layoutManager.scrollToPositionWithOffset(blockStartPos, offset)
        })
    }

    private fun scrollToPositionWithProp(position: Int, prop: Float) {
        val vn = dataVersionNumber.get()
        rv.post(fun() {
            if (vn != dataVersionNumber.get()) return
            if (position >= versesDataModel.itemCount) return

            val firstPos = layoutManager.findFirstVisibleItemPosition()
            val lastPos = layoutManager.findLastVisibleItemPosition()
            val height = if (position in firstPos..lastPos) {
                layoutManager.findViewByPosition(position)?.height ?: return
            } else {
                getMeasuredItemHeight(position)
            }

            rv.stopScroll()
            val paddingNegator = -rv.paddingTop
            val offset = -(prop * height).toInt() + paddingNegator
            layoutManager.scrollToPositionWithOffset(position, offset)
        })
    }

    private fun getMeasuredItemHeight(position: Int): Int {
        // child needed is not on screen, we need to measure

        val viewType = adapter.getItemViewType(position)
        val holder = adapter.createViewHolder(rv, viewType)
        adapter.bindViewHolder(holder, position)
        val child = holder.itemView
        child.measure(
            View.MeasureSpec.makeMeasureSpec(rv.width - rv.paddingLeft - rv.paddingRight, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return child.measuredHeight
    }

    /**
     * Returns -1 if there is no visible child (e.g. when split is collapsed until the height is 0).
     */
    private fun getPositionBasedOnScroll(): Int {
        val pos = layoutManager.findFirstVisibleItemPosition()

        // check if the top one has been scrolled
        val child = layoutManager.findViewByPosition(pos)
        if (child != null) {
            val top = child.top
            if (top == 0) {
                return pos
            }
            val bottom = child.bottom
            return if (bottom > 0) {
                pos
            } else {
                pos + 1
            }
        }

        return pos
    }

    override fun getVerse_1BasedOnScroll(): Int {
        return versesDataModel.getVerse_1FromPosition(getPositionBasedOnScroll())
    }

    override fun pageDown(): VersesController.PressResult {
        val oldPos = layoutManager.findFirstVisibleItemPosition()
        var newPos = layoutManager.findLastVisibleItemPosition()

        if (oldPos == newPos && oldPos < versesDataModel.itemCount - 1) { // in case of very long item
            newPos = oldPos + 1
        }

        // negate padding offset, unless this is the first item
        val paddingNegator = if (newPos == 0) 0 else -rv.paddingTop

        // TODO(VersesView revamp): It previously scrolled smoothly
        layoutManager.scrollToPositionWithOffset(newPos, paddingNegator)

        return VersesController.PressResult.Consumed(versesDataModel.getVerse_1FromPosition(newPos))
    }

    override fun pageUp(): VersesController.PressResult {
        val oldPos = layoutManager.findFirstVisibleItemPosition()
        val targetHeight = (rv.height - rv.paddingTop - rv.paddingBottom).coerceAtLeast(0)

        var totalHeight = 0

        // consider how long the first child has been scrolled up
        val firstChild = layoutManager.findViewByPosition(oldPos)
        if (firstChild != null) {
            totalHeight += -firstChild.top
        }

        var curPos = oldPos
        // try until totalHeight exceeds targetHeight
        while (true) {
            curPos--
            if (curPos < 0) {
                break
            }

            totalHeight += getMeasuredItemHeight(curPos)

            if (totalHeight > targetHeight) {
                break
            }
        }

        var newPos = curPos + 1

        if (oldPos == newPos && oldPos > 0) { // move at least one
            newPos = oldPos - 1
        }

        // negate padding offset, unless this is the first item
        val paddingNegator = if (newPos == 0) 0 else -rv.paddingTop

        // TODO(VersesView revamp): It previously scrolled smoothly
        layoutManager.scrollToPositionWithOffset(newPos, paddingNegator)

        return VersesController.PressResult.Consumed(versesDataModel.getVerse_1FromPosition(newPos))
    }

    override fun verseDown(): VersesController.PressResult {
        val oldVerse_1 = getVerse_1BasedOnScroll()

        val newVerse_1 = if (oldVerse_1 < versesDataModel.verses_.verseCount) {
            oldVerse_1 + 1
        } else {
            oldVerse_1
        }

        rv.stopScroll()
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

        rv.stopScroll()
        scrollToVerse(newVerse_1)
        return VersesController.PressResult.Consumed(newVerse_1)
    }

    override fun setViewVisibility(visibility: Int) {
        rv.visibility = visibility
    }

    override fun setViewPadding(padding: Rect) {
        basePadding.set(padding)
        rv.setPadding(basePadding.left, basePadding.top, basePadding.right, basePadding.bottom)
    }

    override fun setViewScrollbarThumb(thumb: Drawable) {
        rv.setVerticalThumb(thumb)
    }

    override fun setViewLayoutSize(width: Int, height: Int) {
        rv.updateLayoutParams {
            this.width = width
            this.height = height
        }
    }

    override fun callAttentionForVerse(verse_1: Int) {
        val pos = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)
        if (pos == -1) return

        attention.verses_1 += verse_1
        attention.start = System.currentTimeMillis()

        layoutManager.findViewByPosition(pos)?.invalidate()
    }

    override fun setAudioHighlight(verse_1: Int, color: Int) {
        // Fast-path: this method is called every 100ms during playback
        // (mirroring the service's position poll). Bailing out when nothing
        // actually changed avoids a needless findViewByPosition + restart of
        // the LinearSmoothScroller animation.
        if (audioHighlight.verse_1 == verse_1 && audioHighlight.color == color) return

        // Always clear the old row first — even when the new verse_1 is 0 or
        // the same number — so an off-by-one (e.g. timing gap between verses)
        // doesn't leave an orphan overlay behind.
        val previous = audioHighlight.verse_1
        audioHighlight.verse_1 = verse_1
        audioHighlight.color = color
        if (previous != 0 && previous != verse_1) {
            val prevPos = versesDataModel.getPositionIgnoringPericopeFromVerse(previous)
            if (prevPos != -1) {
                setAudioHighlightOnRow(layoutManager.findViewByPosition(prevPos), 0)
            }
        }
        if (verse_1 == 0) return

        val pos = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)
        if (pos == -1) return
        val rowView = layoutManager.findViewByPosition(pos)
        setAudioHighlightOnRow(rowView, color)

        // Skip the smooth scroll when the highlighted row is already fully
        // visible inside the viewport — yanking the page when the verse is
        // sitting right in front of the user is more distracting than helpful.
        if (rowView != null) {
            val rowTop = layoutManager.getDecoratedTop(rowView)
            val rowBottom = layoutManager.getDecoratedBottom(rowView)
            val viewportTop = rv.paddingTop
            val viewportBottom = rv.height - rv.paddingBottom
            if (rowTop >= viewportTop && rowBottom <= viewportBottom) return
        }

        // Smooth-scroll the highlighted verse so its top sits at the upper 10%
        // of the viewport — keeps a thin slice of the previous verse visible
        // for context while leaving room for upcoming verses below.
        val smoothScroller = object : LinearSmoothScroller(rv.context) {
            override fun getVerticalSnapPreference(): Int = SNAP_TO_START

            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int,
            ): Int {
                val boxHeight = boxEnd - boxStart
                val targetTop = boxStart + (boxHeight * 0.10f).toInt()
                return targetTop - viewStart
            }
        }
        smoothScroller.targetPosition = pos
        layoutManager.startSmoothScroll(smoothScroller)
    }

    override fun setEmptyMessage(message: CharSequence?, textColor: Int) {
        rv.emptyMessage = message
        rv.emptyMessagePaint.color = textColor
    }

    fun render() {
        adapter.data = versesDataModel
        adapter.ui = versesUiModel
        adapter.listeners = versesListeners
    }

    /**
     * Apply an audio-highlight color to whichever flavor of verse row is sitting
     * at [view]. The legacy [VerseItem] and the experimental
     * [VerseItemComposeView] both expose the same `audioHighlightColor` property
     * surface, so callers (notably the 100ms polling [setAudioHighlight]) stay
     * unaware of which view type is active.
     */
    private fun setAudioHighlightOnRow(view: View?, color: Int) {
        when (view) {
            is VerseItem -> view.audioHighlightColor = color
            is VerseItemComposeView -> view.audioHighlightColor = color
        }
    }
}

/**
 * For calling attention. All attentioned verses have the same start time.
 * The last call to callAttentionForVerse() decides as when the animation starts.
 */
class Attention(var start: Long = 0L, val verses_1: MutableSet<Int> = mutableSetOf()) {
    fun clear() {
        start = 0L
        verses_1.clear()
    }

    fun hasAny() = start != 0L && verses_1.isNotEmpty()
}

/**
 * Sidecar holding the currently audio-highlighted verse_1 + overlay color.
 * Lives alongside [Attention] so a recycled [VerseItem] can repaint correctly
 * during a rebind even when the controller's direct findViewByPosition path
 * never gets a chance to run (e.g. the user scrolls away from the playing
 * verse and back).
 *
 * `verse_1 = 0` is the "no highlight" sentinel.
 */
class AudioHighlight {
    var verse_1: Int = 0
    var color: Int = 0
}

sealed class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

class VerseTextHolder(private val view: VerseItem) : ItemHolder(view) {
    /**
     * @param index the index of verse
     */
    fun bind(
        data: VersesDataModel,
        ui: VersesUiModel,
        listeners: VersesListeners,
        attention: Attention,
        audioHighlight: AudioHighlight,
        checked: Boolean,
        toggleChecked: (position: Int) -> Unit,
        index: Int,
    ) {
        val verse_1 = index + 1
        val ari = Ari.encodeWithBc(data.ari_bc_, verse_1)
        val text = data.verses_.getVerse(index)
        val verseNumberText = data.verses_.getVerseNumberText(index)
        val highlightInfo = data.versesAttributes.highlightInfoMap_[index]

        val lText = view.lText
        val lVerseNumber = view.lVerseNumber

        val startVerseTextPos = VerseRenderer.render(
            lText = lText,
            lVerseNumber = lVerseNumber,
            isVerseNumberShown = ui.isVerseNumberShown,
            ari = ari,
            text = text,
            verseNumberText = verseNumberText,
            highlightInfo = highlightInfo,
            checked = checked,
            inlineLinkSpanFactory = listeners.inlineLinkSpanFactory_
        )

        val textSizeMult = if (data.verses_ is SingleChapterVerses.WithTextSizeMult) {
            data.verses_.getTextSizeMult(index)
        } else {
            ui.textSizeMult
        }

        Appearances.applyTextAppearance(lText, textSizeMult)
        Appearances.applyVerseNumberAppearance(lVerseNumber, textSizeMult)

        if (checked) { // override text color with black or white!
            val selectedTextColor = TextColorUtil.getForCheckedVerse(Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default))
            lText.setTextColor(selectedTextColor)
            lVerseNumber.setTextColor(selectedTextColor)
        }

        val attributeView = view.attributeView
        attributeView.setScale(scaleForAttributeView(App.services.uiDimensions.applied().fontSize2dp * ui.textSizeMult))
        attributeView.bookmarkCount = data.versesAttributes.bookmarkCountMap_[index]
        attributeView.noteCount = data.versesAttributes.noteCountMap_[index]
        attributeView.progressMarkBits = data.versesAttributes.progressMarkBitsMap_[index]
        attributeView.hasMaps = data.versesAttributes.hasMapsMap_[index]
        attributeView.setAttributeListener(listeners.attributeListener, data.version_, data.versionId_, ari)

        view.checked = checked
        view.collapsed = text.isEmpty() && !attributeView.isShowingSomething
        view.onPinDropped = { presetId ->
            val adapterPosition = bindingAdapterPosition
            if (adapterPosition != -1) {
                listeners.pinDropListener.onPinDropped(presetId, Ari.encodeWithBc(data.ari_bc_, data.getVerse_1FromPosition(adapterPosition)))
            }
        }

        /*
         * Dictionary mode is activated on either of these conditions:
         * 1. user manually activate dictionary mode after selecting verses
         * 2. automatic lookup is on and this verse is selected (checked)
         */
        if (ari in ui.dictionaryModeAris || checked && Preferences.getBoolean(view.context.getString(R.string.pref_autoDictionaryAnalyze_key), view.resources.getBoolean(R.bool.pref_autoDictionaryAnalyze_default))) {
            val renderedText = lText.text
            val verseText = renderedText as? SpannableStringBuilder ?: SpannableStringBuilder(renderedText)

            // we have to exclude the verse numbers from analyze text
            val analyzeString = verseText.toString().substring(startVerseTextPos)

            val uri = "content://org.sabda.kamus.provider/analyze".toUri().buildUpon().appendQueryParameter("text", analyzeString).build()

            try {
                view.context.contentResolver.safeQuery(uri, null, null, null, null)?.use { c ->
                    val col_offset = c.getColumnIndexOrThrow("offset")
                    val col_len = c.getColumnIndexOrThrow("len")
                    val col_key = c.getColumnIndexOrThrow("key")

                    while (c.moveToNext()) {
                        val offset = c.getInt(col_offset)
                        val len = c.getInt(col_len)
                        val key = c.getString(col_key)

                        val word = analyzeString.substring(offset, offset + len)
                        val span = DictionaryLinkSpan(DictionaryLinkInfo(word, key), listeners.dictionaryListener_)
                        verseText.setSpan(span, startVerseTextPos + offset, startVerseTextPos + offset + len, 0)
                    }
                }
                lText.text = verseText
            } catch (e: Exception) {
                AppLog.e(TAG, "Error when querying dictionary content provider", e)
            }
        }

// 			{ // DUMP
// 				Log.d(TAG, "==== DUMP verse " + (id + 1));
// 				SpannedString sb = (SpannedString) lText.getText();
// 				Object[] spans = sb.getSpans(0, sb.length(), Object.class);
// 				for (Object span: spans) {
// 					int start = sb.getSpanStart(span);
// 					int end = sb.getSpanEnd(span);
// 					Log.d(TAG, "Span " + span.getClass().getSimpleName() + " " + start + ".." + end + ": " + sb.toString().substring(start, end));
// 				}
// 			}

        // Do we need to call attention?
        if (attention.hasAny() && verse_1 in attention.verses_1) {
            view.callAttention(attention.start)
        } else {
            view.callAttention(0L)
        }

        // Restore audio highlight on rebind. Setting the same color as already
        // on the view is a no-op (the setter early-returns when value == field),
        // so this doesn't restart the fade animation when the same row scrolls
        // off and back into view while still being the active verse.
        view.audioHighlightColor = if (verse_1 == audioHighlight.verse_1) audioHighlight.color else 0

        // Click listener on the whole item view
        view.setOnClickListener {
            when (ui.verseSelectionMode) {
                VersesController.VerseSelectionMode.none -> {
                }

                VersesController.VerseSelectionMode.singleClick -> {
                    val adapterPosition = bindingAdapterPosition
                    if (adapterPosition != -1) {
                        listeners.selectedVersesListener.onVerseSingleClick(data.getVerse_1FromPosition(adapterPosition))
                    }
                }

                VersesController.VerseSelectionMode.multiple -> {
                    val adapterPosition = bindingAdapterPosition
                    if (adapterPosition != -1) {
                        toggleChecked(adapterPosition)
                    }
                }
            }
        }
    }

    private fun scaleForAttributeView(fontSizeDp: Float) = when {
        fontSizeDp >= 13 /* 72% */ && fontSizeDp < 24 /* 133% */ -> 1f
        fontSizeDp < 8 -> 0.5f // 0 ~ 44%
        fontSizeDp < 18 -> 0.75f // 44% ~ 72%
        fontSizeDp >= 36 -> 2f // 200% ~
        else -> 1.5f // 24 to 36 // 133% ~ 200%
    }
}

/**
 * Experimental Compose-backed mirror of [VerseTextHolder] (REM bug-hunt; gated
 * by [yuku.alkitab.base.settings.ExperimentalFlags.useComposeVerseItem]).
 *
 * Builds the same UI state the legacy holder builds, but routes verse-text
 * formatting through [VerseRendererCompose] (which emits an `AnnotatedString`
 * directly) and hands everything off to [VerseItemComposeView]. Attribute icons
 * are reused from the legacy [yuku.alkitab.base.widget.AttributeView] inside the
 * Compose view so the suspected text-rendering bug stays the only variable.
 */
class VerseTextComposeHolder(private val view: VerseItemComposeView) : ItemHolder(view) {
    fun bind(
        data: VersesDataModel,
        ui: VersesUiModel,
        listeners: VersesListeners,
        attention: Attention,
        audioHighlight: AudioHighlight,
        checked: Boolean,
        toggleChecked: (position: Int) -> Unit,
        index: Int,
    ) {
        val verse_1 = index + 1
        val ari = Ari.encodeWithBc(data.ari_bc_, verse_1)
        val text = data.verses_.getVerse(index)
        val verseNumberText = data.verses_.getVerseNumberText(index)
        val highlightInfo = data.versesAttributes.highlightInfoMap_[index]

        val renderResult = VerseRendererCompose.render(
            isVerseNumberShown = ui.isVerseNumberShown,
            ari = ari,
            text = text,
            verseNumberText = verseNumberText,
            highlightInfo = highlightInfo,
            checked = checked,
        )

        val textSizeMult = if (data.verses_ is SingleChapterVerses.WithTextSizeMult) {
            data.verses_.getTextSizeMult(index)
        } else {
            ui.textSizeMult
        }

        val applied = App.services.uiDimensions.applied()
        val fontSizeDp = applied.fontSize2dp * textSizeMult
        val verseNumberFontSizeDp = applied.fontSize2dp * 0.7f * textSizeMult
        val attributeScale = scaleForAttributeView(applied.fontSize2dp * ui.textSizeMult)

        // Pre-resolve progress-mark captions at bind time so the
        // accessibility path (getContentDescription) doesn't run a DB query
        // on every TalkBack read. Mirrors the values the legacy
        // VerseItem.getContentDescription resolves inline.
        val progressMarkBits = data.versesAttributes.progressMarkBitsMap_[index]
        val progressMarkCaptions: List<String?> = (0 until yuku.alkitab.base.widget.AttributeView.PROGRESS_MARK_TOTAL_COUNT).map { presetId ->
            if (progressMarkBits and (1 shl (yuku.alkitab.base.widget.AttributeView.PROGRESS_MARK_BITS_START + presetId)) == 0) {
                null
            } else {
                App.services.storage.db.getProgressMarkByPresetId(presetId)?.let { progressMark ->
                    if (progressMark.caption.isNullOrEmpty()) {
                        view.context.getString(yuku.alkitab.base.widget.AttributeView.getDefaultProgressMarkStringResource(presetId))
                    } else {
                        progressMark.caption
                    }
                }
            }
        }

        val state = VerseItemComposeState(
            render = renderResult,
            fontSizeDp = fontSizeDp,
            verseNumberFontSizeDp = verseNumberFontSizeDp,
            fontColor = applied.fontColor,
            verseNumberColor = applied.verseNumberColor,
            lineSpacingMult = applied.lineSpacingMult,
            typeface = applied.fontFace,
            fontBold = applied.fontBold,
            attribute = AttributeState(
                bookmarkCount = data.versesAttributes.bookmarkCountMap_[index],
                noteCount = data.versesAttributes.noteCountMap_[index],
                progressMarkBits = progressMarkBits,
                hasMaps = data.versesAttributes.hasMapsMap_[index],
                scale = attributeScale,
                version = data.version_,
                versionId = data.versionId_,
                ari = ari,
                attributeListener = listeners.attributeListener,
                progressMarkCaptions = progressMarkCaptions,
            ),
            onClick = {
                when (ui.verseSelectionMode) {
                    VersesController.VerseSelectionMode.none -> Unit
                    VersesController.VerseSelectionMode.singleClick -> {
                        val adapterPosition = bindingAdapterPosition
                        if (adapterPosition != -1) {
                            listeners.selectedVersesListener.onVerseSingleClick(data.getVerse_1FromPosition(adapterPosition))
                        }
                    }
                    VersesController.VerseSelectionMode.multiple -> {
                        val adapterPosition = bindingAdapterPosition
                        if (adapterPosition != -1) {
                            toggleChecked(adapterPosition)
                        }
                    }
                }
            },
            onInlineLinkClick = { type, arif ->
                // Reuse the same factory as the legacy path so footnote / xref
                // dialogs etc. open with identical semantics.
                listeners.inlineLinkSpanFactory_.create(type, arif).onClick(view)
            },
            onPinDropped = { presetId ->
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != -1) {
                    listeners.pinDropListener.onPinDropped(presetId, Ari.encodeWithBc(data.ari_bc_, data.getVerse_1FromPosition(adapterPosition)))
                }
            },
        )

        val attr = state.attribute
        val attributeShowingSomething = attr.bookmarkCount > 0 ||
            attr.noteCount > 0 ||
            (attr.progressMarkBits and yuku.alkitab.base.widget.AttributeView.PROGRESS_MARK_BIT_MASK) != 0 ||
            attr.hasMaps

        view.bind(state)
        view.checked = checked
        view.collapsed = text.isEmpty() && !attributeShowingSomething

        // Attention: same logic as VerseTextHolder.
        if (attention.hasAny() && verse_1 in attention.verses_1) {
            view.callAttention(attention.start)
        } else {
            view.callAttention(0L)
        }

        // Audio highlight: same poke-on-rebind as VerseTextHolder.
        view.audioHighlightColor = if (verse_1 == audioHighlight.verse_1) audioHighlight.color else 0
    }

    private fun scaleForAttributeView(fontSizeDp: Float) = when {
        fontSizeDp >= 13 && fontSizeDp < 24 -> 1f
        fontSizeDp < 8 -> 0.5f
        fontSizeDp < 18 -> 0.75f
        fontSizeDp >= 36 -> 2f
        else -> 1.5f
    }
}

class PericopeHolder(private val view: PericopeHeaderItem) : ItemHolder(view) {
    /**
     * @param index the index of verse
     */
    fun bind(data: VersesDataModel, ui: VersesUiModel, listeners: VersesListeners, position: Int, index: Int) {
        val pericopeBlock = data.pericopeBlocks_[index]

        val lCaption = view.findViewById<TextView>(R.id.lCaption)
        val lParallels = view.findViewById<TextView>(R.id.lParallels)

        lCaption.text = FormattedTextRenderer.render(pericopeBlock.title)

        // turn off top padding if the position == 0 OR before this is also a pericope title
        val paddingTop = if (position == 0 || data.getItemViewType(position - 1) == ItemType.pericope) {
            0
        } else {
            App.services.uiDimensions.applied().pericopeSpacingTop
        }

        this.itemView.setPadding(0, paddingTop, 0, App.services.uiDimensions.applied().pericopeSpacingBottom)

        Appearances.applyPericopeTitleAppearance(lCaption, ui.textSizeMult)

        // make parallel gone if not exist
        if (pericopeBlock.parallels.isEmpty()) {
            lParallels.visibility = GONE
        } else {
            lParallels.visibility = VISIBLE

            val sb = SpannableStringBuilder("(")

            val total = pericopeBlock.parallels.size
            for (i in 0 until total) {
                val parallel = pericopeBlock.parallels[i]

                if (i > 0) {
                    // force new line for certain parallel patterns
                    if (total == 6 && i == 3 || total == 4 && i == 2 || total == 5 && i == 3) {
                        sb.append("; \n")
                    } else {
                        sb.append("; ")
                    }
                }

                appendParallel(sb, parallel, listeners.parallelListener_)
            }
            sb.append(')')

            lParallels.setText(sb, TextView.BufferType.SPANNABLE)
            Appearances.applyPericopeParallelTextAppearance(lParallels, ui.textSizeMult)
        }
    }

    private fun appendParallel(sb: SpannableStringBuilder, parallel: String, parallelListener: (ParallelClickData) -> Unit) {
        val sb_len = sb.length

        fun link(): Boolean {
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
            if (ariRanges == null || ariRanges.size() == 0) {
                return false
            }

            val display = parallel.substring(targetEndPos + 1)

            // if we reach this, data and display should have values, and we must not go to fallback below
            sb.append(display)
            sb.setSpan(ParallelSpan(AriParallelClickData(ariRanges.get(0)), parallelListener), sb_len, sb.length, 0)
            return true
        }

        val completed = link()
        if (!completed) {
            // fallback if the above code fails
            sb.append(parallel)
            sb.setSpan(ParallelSpan(ReferenceParallelClickData(parallel), parallelListener), sb_len, sb.length, 0)
        }
    }
}

class VersesAdapter(
    private val attention: Attention,
    private val audioHighlight: AudioHighlight,
    private val isChecked: VersesAdapter.(position: Int) -> Boolean,
    private val toggleChecked: VersesAdapter.(position: Int) -> Unit,
) : RecyclerView.Adapter<ItemHolder>() {
    init {
        setHasStableIds(true)
    }

    var data = VersesDataModel.EMPTY
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var ui = VersesUiModel.EMPTY
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var listeners = VersesListeners.EMPTY
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int {
        return data.itemCount
    }

    /**
     * Id assignment for nice animation, keeping verses animated.
     * For verses, it is always verse_1 * 1000
     * For pericopes, it is located between verses, so it is assigned to be the next verse_1 * 1000 - distance to that verse.
     *
     * For example:
     * [verse 1, pericope, verse 2, verse 3, pericope, pericope, verse 4] will have ids
     * [1000, 1999, 2000, 3000, 3998, 3999, 4000]
     */
    override fun getItemId(position: Int): Long {
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

    override fun getItemViewType(position: Int): Int {
        val baseType = data.getItemViewType(position)
        // Experimental: when the flag is on, swap the verseText viewType for a
        // distinct one so RecyclerView pools the Compose-backed rows separately.
        // Picked > Int.MAX_VALUE / 2 to avoid colliding with any future
        // [ItemType] ordinal.
        return when (baseType) {
            ItemType.verseText -> if (ExperimentalFlags.useComposeVerseItem()) VIEW_TYPE_VERSE_TEXT_COMPOSE else baseType.ordinal
            else -> baseType.ordinal
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            ItemType.verseText.ordinal -> {
                VerseTextHolder(inflater.inflate(R.layout.item_verse, parent, false) as VerseItem)
            }

            VIEW_TYPE_VERSE_TEXT_COMPOSE -> {
                VerseTextComposeHolder(VerseItemComposeView(parent.context))
            }

            ItemType.pericope.ordinal -> {
                PericopeHolder(inflater.inflate(R.layout.item_pericope_header, parent, false) as PericopeHeaderItem)
            }

            else -> throw RuntimeException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        when (holder) {
            is VerseTextHolder -> {
                val index = data.getVerse_0(position)
                holder.bind(data, ui, listeners, attention, audioHighlight, isChecked(position), { toggleChecked(it) }, index)
            }

            is VerseTextComposeHolder -> {
                val index = data.getVerse_0(position)
                holder.bind(data, ui, listeners, attention, audioHighlight, isChecked(position), { toggleChecked(it) }, index)
            }

            is PericopeHolder -> {
                val index = data.getPericopeIndex(position)
                holder.bind(data, ui, listeners, position, index)
            }
        }
    }

    companion object {
        /** Distinct viewType for the experimental Compose verse rows. */
        const val VIEW_TYPE_VERSE_TEXT_COMPOSE = 1_000_001
    }
}
