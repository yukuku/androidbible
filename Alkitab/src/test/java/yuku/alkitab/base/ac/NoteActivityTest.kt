package yuku.alkitab.base.ac

import android.app.Application
import android.graphics.Typeface
import android.os.Bundle
import android.os.Looper
import android.widget.FrameLayout
import androidx.appcompat.view.menu.MenuBuilder
import androidx.compose.ui.platform.ComposeView
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.FontManager
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "en-rUS")
@LooperMode(LooperMode.Mode.PAUSED)
class NoteActivityTest {
    class TestActivity : NoteActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_Alkitab)
            super.onCreate(savedInstanceState)
        }
    }

    private lateinit var controller: ActivityController<TestActivity>
    private lateinit var originalServices: AppServices
    private var originalFontName: String? = null
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        Preferences.invalidate()
        originalFontName = Preferences.getString(Prefkey.jenisHuruf)
        Preferences.setString(R.string.pref_language_key, "en")
        Preferences.setBoolean(Prefkey.boldHuruf, false)
        originalServices = App.services
        App.services = AppServices(mockk(), mockk(), S.uiDimensions)
        controller = Robolectric.buildActivity(
            TestActivity::class.java,
            NoteActivity.createNewNoteIntent("Genesis 1:1", 1, 1),
        ).create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        App.services = originalServices
        Preferences.setString(Prefkey.jenisHuruf, originalFontName)
        Preferences.setBoolean(Prefkey.boldHuruf, false)
        S.recalculate()
    }

    @Test
    fun `display panel is available while viewing and editing and back preserves the draft`() {
        activity.tCaption.setText("Unsaved note")
        activity.tCaption.setSelection(4)
        val menu = MenuBuilder(activity)
        activity.onCreateOptionsMenu(menu)
        val display = menu.findItem(R.id.menuDisplay)
        val overlay = activity.findViewById<FrameLayout>(R.id.textAppearanceOverlay)
        val initialChildCount = overlay.childCount

        for (isEditing in listOf(true, false)) {
            activity.setEditingMode(isEditing)
            activity.onPrepareOptionsMenu(menu)
            assertTrue(display.isVisible)
            activity.onOptionsItemSelected(display)
            assertEquals(initialChildCount + 1, overlay.childCount)
            assertTrue(overlay.getChildAt(initialChildCount) is ComposeView)
            activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(initialChildCount, overlay.childCount)
            assertFalse(activity.isFinishing)
            assertEquals("Unsaved note", activity.tCaption.text.toString())
            assertEquals(4, activity.tCaption.selectionStart)
        }
    }

    @Test
    fun `returning from appearance settings applies every font and bold setting to both modes without changing the draft or selection`() {
        activity.tCaption.setText("Keep this draft")
        activity.tCaption.setSelection(5)
        for (fontName in listOf("DEFAULT", "SERIF", "MONOSPACE")) {
            for (isBold in listOf(true, false)) {
                controller.pause().stop()
                Preferences.setString(Prefkey.jenisHuruf, fontName)
                Preferences.setBoolean(Prefkey.boldHuruf, isBold)
                controller.start().resume()
                val expectedStyle = if (isBold) Typeface.BOLD else Typeface.NORMAL
                val expectedTypeface = Typeface.create(FontManager.typeface(fontName), expectedStyle)
                assertEquals(expectedTypeface, activity.tCaption.typeface)
                assertEquals(expectedTypeface, activity.tCaptionReadOnly.typeface)
                assertEquals("Keep this draft", activity.tCaption.text.toString())
                assertEquals(5, activity.tCaption.selectionStart)
            }
        }
    }
}
