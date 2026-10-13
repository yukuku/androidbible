package yuku.alkitab.base.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import yuku.alkitab.debug.R

internal fun updateReaderToolbarLayout(
    root: ViewGroup,
    isFullScreen: Boolean,
    isToolbarAtBottom: Boolean,
    isSelectionActive: Boolean,
    isComposeToolbarEnabled: Boolean,
) {
    val overlay = root.parent as FrameLayout
    val host = root.requireViewById<ViewGroup>(R.id.toolbarHost)
    val selection = overlay.requireViewById<FloatingSelectionToolbar>(R.id.selectionToolbar)
    val isFloating = isFullScreen && isSelectionActive
    val selectionParent = if (isFloating) overlay else host
    if (selection.parent !== selectionParent) {
        (selection.parent as ViewGroup).removeView(selection)
        selectionParent.addView(selection, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    selection.configure(isFloating, isToolbarAtBottom)
    host.isVisible = !isFullScreen
    root.requireViewById<View>(R.id.toolbar).isVisible = !isFullScreen && !isSelectionActive
    root.requireViewById<View>(R.id.composeToolbar).isVisible = !isFullScreen && !isSelectionActive && isComposeToolbarEnabled
    selection.isVisible = isSelectionActive
    val index = if (isToolbarAtBottom) root.childCount - 1 else 0
    if (root.indexOfChild(host) != index) {
        root.removeView(host)
        root.addView(host, index)
    }
}
