package yuku.alkitab.base.verses

import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.util.IntArrayList
import java.util.concurrent.atomic.AtomicInteger

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
        TODO("not implemented")
    }

    override fun scrollToVerse(verse_1: Int) {
        TODO("not implemented")
    }

    override fun scrollToVerse(verse_1: Int, prop: Float) {
        TODO("not implemented")
    }

    override fun getVerse_1BasedOnScroll(): Int {
        TODO("not implemented")
    }

    override fun press(keyCode: Int): VersesController.PressResult {
        TODO("not implemented")
    }

    override fun setPadding(padding: Rect) {
        // TODO("not implemented")
    }

    override fun callAttentionForVerse(verse_1: Int) {
        // TODO("not implemented")
    }

    fun render() {
        TODO()
    }
}
