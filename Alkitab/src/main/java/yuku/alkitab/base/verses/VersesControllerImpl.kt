package yuku.alkitab.base.verses

import android.graphics.Rect
import android.net.Uri
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import yuku.afw.storage.Preferences
import yuku.alkitab.base.S
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.base.verses.VersesDataModel.ItemType
import yuku.alkitab.base.widget.AriParallelClickData
import yuku.alkitab.base.widget.DictionaryLinkInfo
import yuku.alkitab.base.widget.DictionaryLinkSpan
import yuku.alkitab.base.widget.FormattedTextRenderer
import yuku.alkitab.base.widget.ParallelClickData
import yuku.alkitab.base.widget.ParallelSpan
import yuku.alkitab.base.widget.PericopeHeaderItem
import yuku.alkitab.base.widget.ReferenceParallelClickData
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "VersesControllerImpl"

class VersesControllerImpl(
    private val rv: EmptyableRecyclerView,
    override val name: String,
    versesDataModel: VersesDataModel = VersesDataModel.EMPTY,
    versesUiModel: VersesUiModel = VersesUiModel.EMPTY,
    versesListeners: VersesListeners = VersesListeners.EMPTY
) : VersesController {

    private val checkedPositions = mutableSetOf<Int>()
    private val attention = Attention()

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

                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                val firstChild = layoutManager.findViewByPosition(firstVisibleItemPosition) ?: return

                var prop = 0f
                var position = -1

                val remaining = firstChild.bottom // padding top is ignored
                if (remaining >= 0) { // bottom of first child is lower than top padding
                    position = firstVisibleItemPosition
                    prop = 1f - remaining.toFloat() / firstChild.height
                } else { // we should have a second child
                    layoutManager.findViewByPosition(firstVisibleItemPosition + 1)?.let { secondChild ->
                        position = firstVisibleItemPosition + 1
                        prop = (-remaining).toFloat() / secondChild.height
                    }
                }

                val verse_1 = versesDataModel.getVerseOrPericopeFromPosition(position)

                if (scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                    if (verse_1 > 0) {
                        versesListeners.verseScrollListener.onVerseScroll(false, verse_1, prop)
                    } else {
                        versesListeners.verseScrollListener.onVerseScroll(true, 0, 0f)
                    }

                    if (position == 0 && firstChild.top == view.paddingTop) {
                        // we are really at the top
                        versesListeners.verseScrollListener.onScrollToTop()
                    }
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
        val res = IntArrayList(checkedPositions.size)
        for (checkedPosition in checkedPositions) {
            val verse_1 = versesDataModel.getVerse_1FromPosition(checkedPosition)
            if (verse_1 >= 1) {
                res.add(verse_1)
            }
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

        rv.post(fun() {
            // this may happen async from above, so check first if pos is still valid
            if (position >= versesDataModel.itemCount) return

            // negate padding offset, unless this is the first verse
            val paddingNegator = if (position == 0) 0 else -rv.paddingTop

            val firstPos = layoutManager.findFirstVisibleItemPosition()
            val lastPos = layoutManager.findLastVisibleItemPosition()
            if (position in firstPos..lastPos) {
                // we have the child on screen, no need to measure
                val child = layoutManager.findViewByPosition(position) ?: return
                rv.stopScroll()
                layoutManager.scrollToPositionWithOffset(position, -(prop * child.height).toInt() + paddingNegator)
                return
            }

            val measuredHeight = getMeasuredItemHeight(position)
            rv.stopScroll()
            layoutManager.scrollToPositionWithOffset(position, -(prop * measuredHeight).toInt() + paddingNegator)
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
        rv.setPadding(padding.left, padding.top, padding.right, padding.bottom)
    }

    override fun setViewLayoutSize(width: Int, height: Int) {
        val lp = rv.layoutParams
        lp.width = width
        lp.height = height
        rv.layoutParams = lp
    }

    override fun callAttentionForVerse(verse_1: Int) {
        val pos = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)
        if (pos == -1) return

        attention.verses_1 += verse_1
        attention.start = System.currentTimeMillis()

        layoutManager.findViewByPosition(pos)?.invalidate()
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
        checked: Boolean,
        toggleChecked: (position: Int) -> Unit,
        index: Int
    ) {
        val verse_1 = index + 1
        val ari = Ari.encodeWithBc(data.ari_bc_, verse_1)
        val text = data.verses_.getVerse(index)
        val verseNumberText = data.verses_.getVerseNumberText(index)
        val highlightInfo = data.versesAttributes.highlightInfoMap_[index]

        val lText = view.lText
        val lVerseNumber = view.lVerseNumber

        val startVerseTextPos = VerseRenderer.render(lText, lVerseNumber, ari, text, verseNumberText, highlightInfo, checked, listeners.inlineLinkSpanFactory_, null)

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
        attributeView.setScale(scaleForAttributeView(S.applied().fontSize2dp * ui.textSizeMult))
        attributeView.bookmarkCount = data.versesAttributes.bookmarkCountMap_[index]
        attributeView.noteCount = data.versesAttributes.noteCountMap_[index]
        attributeView.progressMarkBits = data.versesAttributes.progressMarkBitsMap_[index]
        attributeView.hasMaps = data.versesAttributes.hasMapsMap_[index]
        attributeView.setAttributeListener(listeners.attributeListener, data.version_, data.versionId_, ari)

        view.checked = checked
        view.collapsed = text.isEmpty() && !attributeView.isShowingSomething
        view.onPinDropped = { presetId ->
            listeners.pinDropListener.onPinDropped(presetId, Ari.encodeWithBc(data.ari_bc_, data.getVerse_1FromPosition(adapterPosition)))
        }

        /*
         * Dictionary mode is activated on either of these conditions:
         * 1. user manually activate dictionary mode after selecting verses
         * 2. automatic lookup is on and this verse is selected (checked)
         */
        if (ari in ui.dictionaryModeAris || checked && Preferences.getBoolean(view.context.getString(R.string.pref_autoDictionaryAnalyze_key), view.resources.getBoolean(R.bool.pref_autoDictionaryAnalyze_default))) {
            val renderedText = lText.text
            val verseText = if (renderedText is SpannableStringBuilder) renderedText else SpannableStringBuilder(renderedText)

            // we have to exclude the verse numbers from analyze text
            val analyzeString = verseText.toString().substring(startVerseTextPos)

            val uri = Uri.parse("content://org.sabda.kamus.provider/analyze").buildUpon().appendQueryParameter("text", analyzeString).build()

            try {
                view.context.contentResolver.query(uri, null, null, null, null)?.use { c ->
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

//			{ // DUMP
//				Log.d(TAG, "==== DUMP verse " + (id + 1));
//				SpannedString sb = (SpannedString) lText.getText();
//				Object[] spans = sb.getSpans(0, sb.length(), Object.class);
//				for (Object span: spans) {
//					int start = sb.getSpanStart(span);
//					int end = sb.getSpanEnd(span);
//					Log.d(TAG, "Span " + span.getClass().getSimpleName() + " " + start + ".." + end + ": " + sb.toString().substring(start, end));
//				}
//			}

        // Do we need to call attention?
        if (attention.hasAny() && verse_1 in attention.verses_1) {
            view.callAttention(attention.start)
        } else {
            view.callAttention(0L)
        }

        // Click listener on the whole item view
        view.setOnClickListener {
            when (ui.verseSelectionMode) {
                VersesController.VerseSelectionMode.none -> {
                }
                VersesController.VerseSelectionMode.singleClick -> {
                    listeners.selectedVersesListener.onVerseSingleClick(data.getVerse_1FromPosition(adapterPosition))
                }
                VersesController.VerseSelectionMode.multiple -> {
                    toggleChecked(adapterPosition)
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
            S.applied().pericopeSpacingTop
        }

        this.itemView.setPadding(0, paddingTop, 0, S.applied().pericopeSpacingBottom)

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
    private val isChecked: VersesAdapter.(position: Int) -> Boolean,
    private val toggleChecked: VersesAdapter.(position: Int) -> Unit
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

    override fun getItemViewType(position: Int) = data.getItemViewType(position).ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            ItemType.verseText.ordinal -> {
                VerseTextHolder(inflater.inflate(R.layout.item_verse, parent, false) as VerseItem)
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
                holder.bind(data, ui, listeners, attention, isChecked(position), { toggleChecked(it) }, index)
            }
            is PericopeHolder -> {
                val index = data.getPericopeIndex(position)
                holder.bind(data, ui, listeners, position, index)
            }
        }
    }
}
