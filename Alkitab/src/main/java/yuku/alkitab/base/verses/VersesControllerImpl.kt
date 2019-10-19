package yuku.alkitab.base.verses

import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.util.IntArrayList
import java.util.concurrent.atomic.AtomicInteger

class VersesControllerImpl(
    override var name: String,
    override var verseSelectionMode: VersesController.VerseSelectionMode,
    override var attributeListener: VersesController.AttributeListener,
    override var listener: VersesController.SelectedVersesListener,
    override var onVerseScrollListener: VersesController.OnVerseScrollListener,
    override var parallelListener_: (VersesController.ParallelClickData) -> Unit,
    override var inlineLinkSpanFactory_: VerseInlineLinkSpan.Factory
) : VersesController {

    private val selectedPositions = mutableSetOf<Int>()

    // TODO check if we still need this
    private val dataVersionNumber = AtomicInteger()

    override var versesDataModel = VersesDataModel.EMPTY
        set(value) {
            field = value
            dataVersionNumber.incrementAndGet()
            render()
        }

    override fun uncheckAllVerses(callListener: Boolean) {
        selectedPositions.clear()

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
                selectedPositions += pos
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

    fun render() {
        TODO()
    }
}