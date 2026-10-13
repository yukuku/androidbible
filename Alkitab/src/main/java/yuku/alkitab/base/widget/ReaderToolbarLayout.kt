package yuku.alkitab.base.widget

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import yuku.alkitab.debug.R

internal fun updateReaderToolbarLayout(
    root: ViewGroup,
    fullScreen: Boolean,
    toolbarAtBottom: Boolean,
    selectionActive: Boolean,
    useComposeToolbar: Boolean,
) {
    val host = root.requireViewById<View>(R.id.toolbarHost)
    host.isVisible = !fullScreen || selectionActive
    root.requireViewById<View>(R.id.toolbar).isVisible = !fullScreen && !selectionActive
    root.requireViewById<View>(R.id.composeToolbar).isVisible = !fullScreen && !selectionActive && useComposeToolbar
    root.requireViewById<View>(R.id.selectionToolbar).isVisible = selectionActive
    val index = if (toolbarAtBottom) root.childCount - 1 else 0
    if (root.indexOfChild(host) != index) {
        root.removeView(host)
        root.addView(host, index)
    }
}
