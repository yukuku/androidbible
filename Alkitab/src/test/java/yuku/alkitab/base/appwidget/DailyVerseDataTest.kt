package yuku.alkitab.base.appwidget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViews
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.br.DailyVerseAppWidgetReceiver
import yuku.alkitab.base.model.VersionImpl
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DailyVerseDataTest {
    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `widget update flow preserves each widget state for an unavailable version`() {
        val intent = slot<Intent>()
        val manager = mockk<AppWidgetManager>(relaxed = true)
        mockkStatic(AppWidgetManager::class)
        mockkConstructor(RemoteViews::class)
        try {
            every { AppWidgetManager.getInstance(any()) } returns manager
            every { anyConstructed<RemoteViews>().setRemoteAdapter(any(), capture(intent)) } just Runs
            every { anyConstructed<RemoteViews>().setInt(any(), any(), any()) } just Runs

            for (appWidgetId in listOf(12345, 12346)) {
                val savedState = DailyVerseData.SavedState().apply {
                    versionId = "file/missing-version"
                    click = 37
                }
                DailyVerseData.saveSavedState(appWidgetId, savedState)
                DailyVerseAppWidgetReceiver.buildUpdate(RuntimeEnvironment.getApplication(), appWidgetId)
                verify { anyConstructed<RemoteViews>().setInt(R.id.background, "setAlpha", 255) }

                assertEquals(appWidgetId, intent.captured.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
                assertSame(VersionImpl.getInternalVersion(), DailyVerseData.getVersion(savedState.versionId))

                val factory = DailyVerseAppWidgetService().onGetViewFactory(intent.captured)
                factory.onCreate()
                factory.onDataSetChanged()

                val reloaded = DailyVerseData.loadSavedState(appWidgetId)
                assertEquals(savedState.versionId, reloaded.versionId)
                assertTrue(reloaded.click in 37..75)
            }
        } finally {
            unmockkConstructor(RemoteViews::class)
            unmockkStatic(AppWidgetManager::class)
        }
    }

    @Test
    fun `unavailable widget verses advance in the requested direction`() {
        val version = mockk<Version>()
        every { version.loadVerseText(any()) } returns null
        for (direction in listOf(-1, 1)) {
            val state = DailyVerseData.SavedState().apply { click = 100 }
            assertNull(DailyVerseData.getAris(12345, state, version, direction))
            assertEquals(100 + 19 * direction, state.click)
            assertEquals(state.click, DailyVerseData.loadSavedState(12345).click)
        }
    }

    @Test
    fun `an unset version uses the internal Bible and survives a settings round trip`() {
        val state = DailyVerseData.SavedState().apply {
            textSize = 18f
            backgroundAlpha = 102
            click = 7
        }
        DailyVerseData.saveSavedState(54321, state)
        val loaded = DailyVerseData.loadSavedState(54321)
        assertNull(loaded.versionId)
        assertEquals(7, loaded.click)
        assertSame(VersionImpl.getInternalVersion(), DailyVerseData.getVersion(loaded.versionId))
        DailyVerseData.saveSavedState(54321, null)
    }

    @Test
    fun `a null saved state deletes the settings and restores preference defaults`() {
        DailyVerseData.saveSavedState(54321, DailyVerseData.SavedState().apply {
            versionId = "internal"
            darkText = true
            hideAppIcon = true
            transparentBackground = true
            textSize = 24f
            backgroundAlpha = 102
            click = 7
        })
        DailyVerseData.saveSavedState(54321, null)
        val loaded = DailyVerseData.loadSavedState(54321)
        assertNull(loaded.versionId)
        assertFalse(loaded.darkText)
        assertFalse(loaded.hideAppIcon)
        assertFalse(loaded.transparentBackground)
        assertEquals(14f, loaded.textSize, 0f)
        assertEquals(255, loaded.backgroundAlpha)
        assertEquals(0, loaded.click)
    }

}
