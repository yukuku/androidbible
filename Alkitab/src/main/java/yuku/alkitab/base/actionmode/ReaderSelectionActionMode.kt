package yuku.alkitab.base.actionmode

import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.widget.Toolbar

class ReaderSelectionActionMode(
    private val toolbar: Toolbar,
    private val callback: Callback,
) : ActionMode() {
    private var finished = false
    private var customView: View? = null

    fun start(): Boolean {
        toolbar.menu.clear()
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { !finished && callback.onActionItemClicked(this, it) }
        return callback.onCreateActionMode(this, menu)
    }

    override fun invalidate() {
        if (!finished) callback.onPrepareActionMode(this, menu)
    }

    override fun finish() {
        if (finished) return
        finished = true
        toolbar.dismissPopupMenus()
        toolbar.setNavigationOnClickListener(null)
        toolbar.setOnMenuItemClickListener(null)
        toolbar.menu.clear()
        callback.onDestroyActionMode(this)
    }

    override fun getMenu(): Menu = toolbar.menu
    override fun getMenuInflater(): MenuInflater = SupportMenuInflater(toolbar.context)
    override fun getTitle(): CharSequence? = toolbar.title
    override fun setTitle(title: CharSequence?) { toolbar.title = title }
    override fun setTitle(resId: Int) { toolbar.setTitle(resId) }
    override fun getSubtitle(): CharSequence? = toolbar.subtitle
    override fun setSubtitle(subtitle: CharSequence?) { toolbar.subtitle = subtitle }
    override fun setSubtitle(resId: Int) { toolbar.setSubtitle(resId) }
    override fun getCustomView(): View? = customView
    override fun setCustomView(view: View?) {
        customView?.let(toolbar::removeView)
        customView = view
        view?.let(toolbar::addView)
    }
}
