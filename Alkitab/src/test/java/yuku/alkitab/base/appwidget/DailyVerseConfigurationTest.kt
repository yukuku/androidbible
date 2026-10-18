package yuku.alkitab.base.appwidget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.SeekBar
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DailyVerseConfigurationTest {
    private lateinit var originalServices: AppServices
    private val widgetManager = mockk<AppWidgetManager>(relaxed = true)

    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        Preferences.invalidate()
        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns widgetManager
        every { widgetManager.getAppWidgetIds(any()) } returns intArrayOf(42)
        originalServices = App.services
        val versions = mockk<VersionManager>()
        every { versions.getAvailableVersions() } returns listOf(
            MVersionInternal().apply { longName = "Internal Bible" },
            MVersionDb().apply {
                filename = "/test/other.yes"
                longName = "Other Bible"
            },
        )
        App.services = AppServices(originalServices.storage, versions, originalServices.uiDimensions)
        DailyVerseData.saveSavedState(42, DailyVerseData.SavedState().apply {
            versionId = "internal"
            textSize = 22f
            transparentBackground = true
            backgroundAlpha = 102
            darkText = true
            hideAppIcon = true
            click = 37
        })
    }

    @After
    fun tearDown() {
        App.services = originalServices
        DailyVerseData.saveSavedState(42, null)
        unmockkStatic(AppWidgetManager::class)
    }

    private fun buildController() = Robolectric.buildActivity(
        DailyVerseAppWidgetConfigurationActivity::class.java,
        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 42),
    )

    @Test
    fun `reconfiguration loads settings and saves appearance without resetting navigation`() {
        val controller = buildController().setup()
        val activity = controller.get()
        try {
            assertEquals(0, activity.selectedVersionPosition)
            assertTrue(activity.findViewById<Button>(R.id.bOk).isEnabled)
            assertEquals(14, activity.findViewById<SeekBar>(R.id.sbTextSize).progress)
            assertEquals(12, activity.findViewById<SeekBar>(R.id.sbTransparent).progress)
            assertTrue(activity.findViewById<CheckBox>(R.id.cTransparentBackground).isChecked)
            assertTrue(activity.findViewById<CheckBox>(R.id.cDarkText).isChecked)
            assertTrue(activity.findViewById<CheckBox>(R.id.cHideAppIcon).isChecked)
            activity.findViewById<CheckBox>(R.id.cTransparentBackground).isChecked = false
            activity.findViewById<SeekBar>(R.id.sbTextSize).progress = 16
            activity.findViewById<Button>(R.id.bOk).performClick()
            val saved = DailyVerseData.loadSavedState(42)
            assertEquals("internal", saved.versionId)
            assertEquals(24f, saved.textSize, 0f)
            assertFalse(saved.transparentBackground)
            assertFalse(saved.darkText)
            assertEquals(255, saved.backgroundAlpha)
            assertEquals(37, saved.click)
            verify { widgetManager.updateAppWidget(42, any()) }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `cancel discards edits and recreation keeps the selected version`() {
        val controller = buildController().setup()
        val activity = controller.get()
        val state = Bundle()
        activity.findViewById<ListView>(R.id.lsVersionsAppWidget).performItemClick(View(activity), 1, 1)
        activity.findViewById<SeekBar>(R.id.sbTextSize).progress = 20
        controller.saveInstanceState(state).pause().stop().destroy()
        val restored = buildController().create(state).start().restoreInstanceState(state).resume()
        try {
            assertEquals(1, restored.get().selectedVersionPosition)
            assertTrue(restored.get().findViewById<Button>(R.id.bOk).isEnabled)
            assertEquals(20, restored.get().findViewById<SeekBar>(R.id.sbTextSize).progress)
            restored.get().findViewById<Button>(R.id.bCancel).performClick()
            val saved = DailyVerseData.loadSavedState(42)
            assertEquals(22f, saved.textSize, 0f)
            assertTrue(saved.transparentBackground)
            assertEquals(37, saved.click)
        } finally {
            restored.pause().stop().destroy()
        }
    }
}
