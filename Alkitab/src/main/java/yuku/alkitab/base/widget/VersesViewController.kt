package yuku.alkitab.base.widget

import androidx.recyclerview.widget.RecyclerView
import yuku.alkitab.model.Version
import java.util.concurrent.atomic.AtomicInteger

class VersesViewController(val rv: RecyclerView) {
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

    var attributeListener = object : AttributeListener() {}

    /**
     * Name of this [VersesViewController] for debugging.
     */
    var name = ""

    override fun toString(): String {
        return "VersesView{name=$name}"
    }

    var verseSelectionMode = VerseSelectionMode.multiple
        set(_) = render()

    var listener = object: SelectedVersesListener() {}

    var onVerseScrollListener = object : OnVerseScrollListener() {}

    /**
     * Updated every time [setData] or [setDataEmpty] is called.
     * Used to track data changes, so delayed scroll, etc can be prevented from happening if the data has changed.
     */
    private val dataVersionNumber = AtomicInteger()


    // Ultimate method

    fun render() {
        when (verseSelectionMode) {
            VerseSelectionMode.singleClick -> {
                //            setSelector(originalSelector)
                //            uncheckAllVerses(false)
                //            setChoiceMode(ListView.CHOICE_MODE_NONE)
            }
            VerseSelectionMode.multiple -> {
                //            setSelector(ColorDrawable(0x0))
                //            setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
            }
            VerseSelectionMode.none -> {
                //            setSelector(ColorDrawable(0x0))
                //            setChoiceMode(ListView.CHOICE_MODE_NONE)
            }
        }
    }
}