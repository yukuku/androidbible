package yuku.alkitab.base.verses

import android.view.ViewGroup
import yuku.alkitab.base.settings.ExperimentalFlags

/**
 * Builds the verses controller for one verse list, whether it is a reader
 * split pane or a dialog.
 *
 * With the Verse (Compose) experimental setting enabled, [recyclerView] is
 * swapped in place for a [VersesComposeView], keeping the same view id, child
 * index, and layout params so the split-view manager, the inset listeners, and
 * the DEBUG layout assertions keep operating on the pane.
 */
fun createVersesController(
    recyclerView: EmptyableRecyclerView,
    name: String,
    versesDataModel: VersesDataModel = VersesDataModel.EMPTY,
    versesUiModel: VersesUiModel = VersesUiModel.EMPTY,
    versesListeners: VersesListeners = VersesListeners.EMPTY,
): VersesController {
    if (!ExperimentalFlags.useComposeVerseItem()) {
        return VersesControllerImpl(recyclerView, name, versesDataModel, versesUiModel, versesListeners)
    }

    val parent = recyclerView.parent as ViewGroup
    val childIndex = parent.indexOfChild(recyclerView)
    val layoutParams = recyclerView.layoutParams
    parent.removeViewAt(childIndex)

    val composeView = VersesComposeView(recyclerView.context)
    composeView.id = recyclerView.id
    parent.addView(composeView, childIndex, layoutParams)

    return VersesComposeControllerImpl(composeView, name, versesDataModel, versesUiModel, versesListeners)
}
