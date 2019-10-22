package yuku.alkitab.base.verses

import android.graphics.Rect
import android.view.Choreographer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.util.IntArrayList
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "VersesControllerImpl"

class VersesControllerImpl(
    private val rv: RecyclerView,
    override val name: String,
    override val verseSelectionMode: VersesController.VerseSelectionMode,
    override val attributeListener: VersesController.AttributeListener,
    override val listener: VersesController.SelectedVersesListener,
    override val onVerseScrollListener: VersesController.OnVerseScrollListener,
    override val parallelListener_: (VersesController.ParallelClickData) -> Unit,
    override val inlineLinkSpanFactory_: VerseInlineLinkSpan.Factory
) : VersesController {
    private val checkedPositions = mutableSetOf<Int>()

    // TODO check if we still need this
    private val dataVersionNumber = AtomicInteger()

    override var versesDataModel = VersesDataModel.EMPTY
        set(value) {
            field = value
            dataVersionNumber.incrementAndGet()
            render()
        }

    override fun uncheckAllVerses(callListener: Boolean) {
        checkedPositions.clear()

        if (callListener) {
            listener.onNoVersesSelected(this)
        }

        render()
    }

    override fun checkVerses(verses_1: IntArrayList, callListener: Boolean) {
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

        if (callListener) {
            if (checked_count > 0) {
                listener.onSomeVersesSelected(this)
            } else {
                listener.onNoVersesSelected(this)
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

                // TODO unsafe cast to LinearLayoutManager
                val linearLayoutManager = rv.layoutManager as LinearLayoutManager
                linearLayoutManager.scrollToPositionWithOffset(position, paddingNegator)
            }
        }
    }

    override fun scrollToVerse(verse_1: Int, prop: Float) {
        val position = versesDataModel.getPositionIgnoringPericopeFromVerse(verse_1)

        if (position == -1) {
            AppLog.d(TAG, "could not find verse_1: $verse_1")
            return
        }

        rv.post {
            // this may happen async from above, so check first if pos is still valid
            if (position >= versesDataModel.itemCount) return@post

            // negate padding offset, unless this is the first verse
            val paddingNegator = if (position == 0) 0 else -rv.paddingTop

            // TODO unsafe cast to LinearLayoutManager
            val linearLayoutManager = rv.layoutManager as LinearLayoutManager
            val firstPos = linearLayoutManager.findFirstVisibleItemPosition()
            val lastPos = linearLayoutManager.findLastVisibleItemPosition()
            if (position in firstPos..lastPos) {
                // we have the child on screen, no need to measure
                val child = linearLayoutManager.getChildAt(position - firstPos) ?: return@post
                linearLayoutManager.scrollToPositionWithOffset(position, -(prop * child.height).toInt() + paddingNegator)
                return@post
            }

            val measuredHeight = getMeasuredItemHeight(position)
            linearLayoutManager.scrollToPositionWithOffset(position, -(prop * measuredHeight).toInt() + paddingNegator)
        }
    }

    private fun getMeasuredItemHeight(position: Int): Int {
        // child needed is not on screen, we need to measure
        // TODO unsafe cast to LinearLayoutManager
        val linearLayoutManager = rv.layoutManager as LinearLayoutManager

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
        Choreographer.getInstance().postFrameCallback {
            render()
        }
    }

    fun render() {
        TODO()
    }
}
