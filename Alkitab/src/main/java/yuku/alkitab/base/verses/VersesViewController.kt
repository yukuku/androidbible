package yuku.alkitab.base.widget

import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.atomic.AtomicInteger

class VersesViewController(val rv: RecyclerView) {


    /**
     * Updated every time [setData] or [setDataEmpty] is called.
     * Used to track data changes, so delayed scroll, etc can be prevented from happening if the data has changed.
     */
    private val dataVersionNumber = AtomicInteger()


    // Ultimate method

    fun render() {
//        when (verseSelectionMode) {
//            VersesController.VerseSelectionMode.singleClick -> {
//                //            setSelector(originalSelector)
//                //            uncheckAllVerses(false)
//                //            setChoiceMode(ListView.CHOICE_MODE_NONE)
//            }
//            VerseSelectionMode.multiple -> {
//                //            setSelector(ColorDrawable(0x0))
//                //            setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
//            }
//            VerseSelectionMode.none -> {
//                //            setSelector(ColorDrawable(0x0))
//                //            setChoiceMode(ListView.CHOICE_MODE_NONE)
//            }
//        }
    }
}