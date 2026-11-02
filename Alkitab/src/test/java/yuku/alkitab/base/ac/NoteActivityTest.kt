package yuku.alkitab.base.ac

import android.app.Application
import android.graphics.Typeface
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.view.menu.MenuBuilder
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `viewing and editing menus exclude appearance controls and preserve the draft`() {
        activity.tCaption.setText("Unsaved note")
        activity.tCaption.setSelection(4)
        val menu = MenuBuilder(activity)
        activity.onCreateOptionsMenu(menu)

        for (isEditing in listOf(true, false)) {
            activity.setEditingMode(isEditing)
            activity.onPrepareOptionsMenu(menu)
            assertEquals(setOf(R.id.menuEdit, R.id.menuDelete, R.id.menuOk), (0 until menu.size()).map { menu.getItem(it).itemId }.toSet())
            assertEquals(!isEditing, menu.findItem(R.id.menuEdit).isVisible)
            assertEquals(isEditing, menu.findItem(R.id.menuDelete).isVisible)
            assertEquals(isEditing, menu.findItem(R.id.menuOk).isVisible)
            assertFalse(activity.isFinishing)
            assertEquals("Unsaved note", activity.tCaption.text.toString())
            assertEquals(4, activity.tCaption.selectionStart)
        }
    }

    @Test
    fun `returning to a note applies shared font and bold settings to both modes without changing the draft or selection`() {
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
