package yuku.alkitab.base.appwidget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
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
            assertEquals(DailyVerseConfiguration("internal", 22, true, 12, true, true), activity.configuration)
            activity.configuration = activity.configuration.copy(isTransparent = false, textSize = 24)
            activity.saveConfiguration()
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
        activity.configuration = activity.configuration.copy(versionId = "file//test/other.yes", textSize = 28, transparency = 3, isIconHidden = false)
        controller.saveInstanceState(state).pause().stop().destroy()
        val restored = buildController().create(state).start().restoreInstanceState(state).resume()
        try {
            assertEquals(activity.configuration, restored.get().configuration)
            restored.get().finish()
            val saved = DailyVerseData.loadSavedState(42)
            assertEquals(22f, saved.textSize, 0f)
            assertTrue(saved.transparentBackground)
            assertEquals(37, saved.click)
        } finally {
            restored.pause().stop().destroy()
        }
    }
    @Test
    fun `saving an unavailable version does not change widget settings`() {
        val controller = buildController().setup()
        try {
            controller.get().configuration = controller.get().configuration.copy(versionId = "missing")
            controller.get().saveConfiguration()
            assertEquals("internal", DailyVerseData.loadSavedState(42).versionId)
            assertFalse(controller.get().isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }

}
