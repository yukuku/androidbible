package yuku.alkitab.base

import androidx.appcompat.view.ActionMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import yuku.alkitab.base.widget.TextAppearancePanel

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34])
class IsiActivityBackNavigationTest {
    private lateinit var controller: ActivityController<IsiActivity>
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(IsiActivity::class.java).create().start().resume()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun `normal reading leaves back navigation to the system`() {
        assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `back closes the appearance panel without leaving the reader`() {
        val panel = mockk<TextAppearancePanel>(relaxed = true)
        activity.textAppearancePanel = panel
        assertTrue(activity.onBackPressedDispatcher.hasEnabledCallbacks())

        activity.onBackPressedDispatcher.onBackPressed()

        verify(exactly = 1) { panel.hide() }
        assertNull(activity.textAppearancePanel)
        assertFalse(activity.isFinishing)
        assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `back clears selection before closing the appearance panel and exiting fullscreen`() {
        activity.setFullScreenWithDrawerHandle(true)
        val panel = mockk<TextAppearancePanel>(relaxed = true)
        activity.textAppearancePanel = panel
        val selection = mockk<ActionMode>(relaxed = true)
        every { selection.finish() } answers { activity.actionMode = null }
        activity.actionMode = selection

        activity.onBackPressedDispatcher.onBackPressed()

        verify(exactly = 1) { selection.finish() }
        verify(exactly = 0) { panel.hide() }
        assertNull(activity.actionMode)
        assertTrue(isFullscreen())
        assertTrue(activity.onBackPressedDispatcher.hasEnabledCallbacks())

        activity.onBackPressedDispatcher.onBackPressed()

        verify(exactly = 1) { panel.hide() }
        assertNull(activity.textAppearancePanel)
        assertTrue(isFullscreen())
        assertTrue(activity.onBackPressedDispatcher.hasEnabledCallbacks())

        activity.onBackPressedDispatcher.onBackPressed()

        assertFalse(isFullscreen())
        assertFalse(activity.isFinishing)
        assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `closing transient reader controls releases the back callback without pressing back`() {
        activity.setFullScreen(true)
        activity.textAppearancePanel = mockk(relaxed = true)
        activity.actionMode = mockk(relaxed = true)
        assertTrue(activity.onBackPressedDispatcher.hasEnabledCallbacks())

        activity.actionMode = null
        activity.textAppearancePanel = null
        activity.setFullScreen(false)

        assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    private fun isFullscreen(): Boolean = IsiActivity::class.java.getDeclaredField("fullScreen")
        .apply { isAccessible = true }.getBoolean(activity)
}
