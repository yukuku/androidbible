package yuku.alkitab.base.actionmode

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.App
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34])
class ReaderSelectionActionModeTest {
    @Test
    fun `toolbar dispatches nested menu actions and close finishes only once even during reentry`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_Alkitab)
        val toolbar = Toolbar(activity)
        toolbar.setNavigationIcon(R.drawable.ic_close)
        toolbar.setNavigationContentDescription(R.string.desc_close)
        var clicked = 0
        var prepared = 0
        var destroyed = 0
        val mode = ReaderSelectionActionMode(toolbar, object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.context_isi, menu)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                prepared++
                return true
            }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                clicked = item.itemId
                return true
            }
            override fun onDestroyActionMode(mode: ActionMode) {
                destroyed++
                mode.finish()
                mode.invalidate()
            }
        })
        assertTrue(mode.start())
        mode.invalidate()
        assertTrue(toolbar.menu.performIdentifierAction(R.id.menuAddNote, 0))
        assertEquals(R.id.menuAddNote, clicked)
        toolbar.children.first { it.contentDescription == activity.getString(R.string.desc_close) }.performClick()
        mode.finish()
        mode.invalidate()
        assertEquals(1, destroyed)
        assertEquals(1, prepared)
        assertEquals(0, toolbar.menu.size())
    }
}
