package yuku.alkitab.base.widget

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import yuku.alkitab.debug.R

internal fun updateReaderToolbarLayout(
    root: ViewGroup,
    isFullScreen: Boolean,
    isToolbarAtBottom: Boolean,
    isSelectionActive: Boolean,
    isComposeToolbarEnabled: Boolean,
) {
    val host = root.requireViewById<View>(R.id.toolbarHost)
    host.isVisible = !isFullScreen || isSelectionActive
    root.requireViewById<View>(R.id.toolbar).isVisible = !isFullScreen && !isSelectionActive
    root.requireViewById<View>(R.id.composeToolbar).isVisible = !isFullScreen && !isSelectionActive && isComposeToolbarEnabled
    root.requireViewById<View>(R.id.selectionToolbar).isVisible = isSelectionActive
    val index = if (isToolbarAtBottom) root.childCount - 1 else 0
    if (root.indexOfChild(host) != index) {
        root.removeView(host)
        root.addView(host, index)
    }
}
