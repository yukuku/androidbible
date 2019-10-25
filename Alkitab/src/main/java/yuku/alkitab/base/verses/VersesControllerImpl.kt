package yuku.alkitab.base.verses

import android.database.Cursor
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
import yuku.alkitab.base.U
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.verses.VersesDataModel.ItemType
import yuku.alkitab.base.widget.AriParallelClickData
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.DictionaryLinkInfo
import yuku.alkitab.base.widget.DictionaryLinkSpan
import yuku.alkitab.base.widget.FormattedTextRenderer
import yuku.alkitab.base.widget.ParallelClickData
import yuku.alkitab.base.widget.ParallelSpan
import yuku.alkitab.base.widget.PericopeHeaderItem
import yuku.alkitab.base.widget.ReferenceParallelClickData
import yuku.alkitab.base.widget.VerseItem
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.base.widget.VerseTextView
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "VersesControllerImpl"

class VersesControllerImpl(
    private val rv: RecyclerView,
    override val name: String,
    versesDataModel: VersesDataModel = VersesDataModel.EMPTY,
    versesUiModel: VersesUiModel = VersesUiModel.EMPTY,
    versesListeners: VersesListeners = VersesListeners.EMPTY
) : VersesController {

    private val checkedPositions = mutableSetOf<Int>()

    // TODO check if we still need this
    private val dataVersionNumber = AtomicInteger()

    private val layoutManager: LinearLayoutManager
    private val adapter: VersesAdapter

    init {
        val layoutManager = LinearLayoutManager(rv.context)
        this.layoutManager = layoutManager
        rv.layoutManager = layoutManager

        val adapter = VersesAdapter(::isChecked)
        this.adapter = adapter
        rv.adapter = adapter
    }

    /**
     * Data for adapter: Verse data
     */
    override var versesDataModel = versesDataModel
        set(value) {
            field = value
            dataVersionNumber.incrementAndGet()
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
        checkedPositions.clear()

        if (callSelectedVersesListener) {
            versesListeners.selectedVersesListener.onNoVersesSelected(this)
        }

        render()
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

        if (callSelectedVersesListener) {
            if (checked_count > 0) {
                versesListeners.selectedVersesListener.onSomeVersesSelected(this)
            } else {
                versesListeners.selectedVersesListener.onNoVersesSelected(this)
            }
        }

        render()
    }

    override fun getCheckedVerses_1(): IntArrayList {
        val res = IntArrayList(checkedPositions.size)
        for (checkedPosition in checkedPositions) {
            val verse_1 = versesDataModel.getVerseFromPosition(checkedPosition)
            if (verse_1 >= 1) {
                res.add(verse_1)
            }
        }
        return res
    }

    private fun isChecked(verse_1: Int): Boolean {
        val pos = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)
        return pos in checkedPositions
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
                val child = layoutManager.getChildAt(position - firstPos) ?: return
                layoutManager.scrollToPositionWithOffset(position, -(prop * child.height).toInt() + paddingNegator)
                return
            }

            val measuredHeight = getMeasuredItemHeight(position)
            layoutManager.scrollToPositionWithOffset(position, -(prop * measuredHeight).toInt() + paddingNegator)
        })
    }

    private fun getMeasuredItemHeight(position: Int): Int {
        // child needed is not on screen, we need to measure

        // TODO(VersesView revamp): create adapter
//        val child = adapter.getView(position, convertView, this)
//        child.measure(View.MeasureSpec.makeMeasureSpec(this.getWidth() - this.getPaddingLeft() - this.getPaddingRight(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
//        scrollToVerseConvertViews[itemType] = child
//        return child.getMeasuredHeight()
        TODO()
    }

    override fun getVerse_1BasedOnScroll(): Int {
        TODO("not implemented")
    }

    override fun press(keyCode: Int): VersesController.PressResult {
        TODO("not implemented")
    }

    override fun setViewVisibility(visibility: Int) {
        rv.visibility = visibility
    }

    override fun setViewPadding(padding: Rect) {
        // TODO("not implemented")
    }

    override fun setViewLayoutSize(width: Int, height: Int) {
        val lp = rv.layoutParams
        lp.width = width
        lp.height = height
        rv.layoutParams = lp
    }

    override fun callAttentionForVerse(verse_1: Int) {
        // TODO("not implemented")
    }

    override fun setDictionaryModeAris(aris: Set<Int>) = TODO()

    override fun invalidate() {
        rv.postOnAnimation {
            render()
        }
    }

    fun render() {
        adapter.data = versesDataModel
        adapter.ui = versesUiModel
        adapter.listeners = versesListeners
    }
}

sealed class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

class VerseTextHolder(private val view: VerseItem) : ItemHolder(view) {
    private val lText: VerseTextView = view.lText
    private val lVerseNumber: TextView = view.lVerseNumber
    private val attributeView: AttributeView = view.attributeView

    /**
     * @param index the index of verse
     */
    fun bind(
        data: VersesDataModel,
        ui: VersesUiModel,
        listeners: VersesListeners,
        checked: Boolean,
        index: Int
    ) {
        val verse_1 = index + 1
        val ari = Ari.encodeWithBc(data.ari_bc_, verse_1)
        val text = data.verses_.getVerse(index)
        val verseNumberText = data.verses_.getVerseNumberText(index)
        val highlightInfo = data.versesAttributes.highlightInfoMap_[index]

        val lText = this.lText
        val lVerseNumber = this.lVerseNumber

        val startVerseTextPos = VerseRenderer.render(lText, lVerseNumber, ari, text, verseNumberText, highlightInfo, checked, listeners.inlineLinkSpanFactory_, null)

        val textSizeMult = if (data.verses_ is SingleChapterVerses.WithTextSizeMult) {
            data.verses_.getTextSizeMult(index)
        } else {
            ui.textSizeMult
        }

        Appearances.applyTextAppearance(lText, textSizeMult)
        Appearances.applyVerseNumberAppearance(lVerseNumber, textSizeMult)

        if (checked) { // override text color with black or white!
            val selectedTextColor = U.getTextColorForSelectedVerse(Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default))
            lText.setTextColor(selectedTextColor)
            lVerseNumber.setTextColor(selectedTextColor)
        }

        val attributeView = this.attributeView
        attributeView.setScale(scaleForAttributeView(S.applied().fontSize2dp * ui.textSizeMult))
        attributeView.bookmarkCount = data.versesAttributes.bookmarkCountMap_[index]
        attributeView.noteCount = data.versesAttributes.noteCountMap_[index]
        attributeView.progressMarkBits = data.versesAttributes.progressMarkBitsMap_[index]
        attributeView.hasMaps = data.versesAttributes.hasMapsMap_[index]
        attributeView.setAttributeListener(listeners.attributeListener, data.version_, data.versionId_, ari)

        view.setCollapsed(text.isEmpty() && !attributeView.isShowingSomething)

        view.setAri(ari)

        /*
         * Dictionary mode is activated on either of these conditions:
         * 1. user manually activate dictionary mode after selecting verses
         * 2. automatic lookup is on and this verse is selected (checked)
         */
        if (ari in ui.dictionaryModeAris || checked && Preferences.getBoolean(view.context.getString(R.string.pref_autoDictionaryAnalyze_key), view.resources.getBoolean(R.bool.pref_autoDictionaryAnalyze_default))) {
            val cr = view.context.contentResolver

            val renderedText = lText.text
            val verseText = if (renderedText is SpannableStringBuilder) renderedText else SpannableStringBuilder(renderedText)

            // we have to exclude the verse numbers from analyze text
            val analyzeString = verseText.toString().substring(startVerseTextPos)

            val uri = Uri.parse("content://org.sabda.kamus.provider/analyze").buildUpon().appendQueryParameter("text", analyzeString).build()
            var c: Cursor? = null
            try {
                c = cr.query(uri, null, null, null, null)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error when querying dictionary content provider", e)
            }

            if (c != null) {
                try {
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
                } finally {
                    c.close()
                }

                lText.text = verseText
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

        // TODO Do we need to call attention?
//        if (attentionStart_ != 0L && attentionPositions_ != null && attentionPositions_.contains(position)) {
//            res.callAttention(attentionStart_)
//        } else {
//            res.callAttention(0)
//        }
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

class VersesAdapter(private val isChecked: (verse_1: Int) -> Boolean) : RecyclerView.Adapter<ItemHolder>() {

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

    override fun getItemCount() = data.itemCount

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
                val verse_1 = index + 1

                holder.bind(data, ui, listeners, isChecked(verse_1), index)
            }
            is PericopeHolder -> {
                val index = data.getPericopeIndex(position)
                holder.bind(data, ui, listeners, position, index)
            }
        }
    }
}
