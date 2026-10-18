package yuku.alkitab.base.appwidget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DailyVerseFactoryTest {
    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        mockkStatic(DailyVerseData::class)
        mockkConstructor(RemoteViews::class)
        every { anyConstructed<RemoteViews>().setTextViewText(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextColor(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setFloat(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setOnClickFillInIntent(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkConstructor(RemoteViews::class)
        unmockkStatic(DailyVerseData::class)
    }

    @Test
    fun `header and body keep the loaded selection until the next dataset change`() {
        val state = DailyVerseData.SavedState().apply {
            versionId = "first"
            textSize = 18f
            darkText = true
        }
        val nextState = DailyVerseData.SavedState().apply {
            versionId = "second"
            textSize = 24f
        }
        val version = mockk<Version>()
        val nextVersion = mockk<Version>()
        val ari = 0x000101
        val nextAri = 0x000202
        every { DailyVerseData.loadSavedState(7) } returns state
        every { DailyVerseData.getVersion("first") } returns version
        every { DailyVerseData.getAris(7, state, version, 1) } returns intArrayOf(ari)
        every { version.referenceWithVerseCount(ari, 1) } returns "Genesis 1:1"
        every { version.loadVerseText(ari) } returns "First verse"
        val factory = DailyVerseFactory(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7))
        factory.onCreate()

        every { DailyVerseData.loadSavedState(7) } returns nextState
        every { DailyVerseData.getVersion("second") } returns nextVersion
        every { DailyVerseData.getAris(7, nextState, nextVersion, any()) } returns intArrayOf(nextAri)
        every { nextVersion.referenceWithVerseCount(nextAri, 1) } returns "Genesis 2:2"
        every { nextVersion.loadVerseText(nextAri) } returns "Second verse"

        factory.getViewAt(0)
        factory.getViewAt(1)
        verify(exactly = 1) { DailyVerseData.loadSavedState(7) }
        verify(exactly = 1) { DailyVerseData.getAris(any(), any(), any(), any()) }
        verify(exactly = 0) { DailyVerseData.saveSavedState(any(), any()) }
        verify { anyConstructed<RemoteViews>().setTextViewText(R.id.tReference, "Genesis 1:1") }
        verify { anyConstructed<RemoteViews>().setTextViewText(R.id.text1, match { it.toString() == "First verse" }) }
        verify { anyConstructed<RemoteViews>().setTextColor(R.id.text1, Color.BLACK) }
        verify { anyConstructed<RemoteViews>().setFloat(R.id.text1, "setTextSize", 18f) }
        verify {
            anyConstructed<RemoteViews>().setOnClickFillInIntent(R.id.tReference, match {
                it.getStringExtra("app_widget_action") == "open_verse" && it.getIntExtra("ari", 0) == ari
            })
        }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) }

        factory.onDataSetChanged()
        factory.getViewAt(0)
        factory.getViewAt(1)
        verify { anyConstructed<RemoteViews>().setTextViewText(R.id.tReference, "Genesis 2:2") }
        verify { anyConstructed<RemoteViews>().setTextViewText(R.id.text1, match { it.toString() == "Second verse" }) }
        verify { anyConstructed<RemoteViews>().setFloat(R.id.text1, "setTextSize", 24f) }
    }

    @Test
    fun `rendering an unavailable selection does not search again or change navigation state`() {
        val state = DailyVerseData.SavedState()
        val version = mockk<Version>()
        every { DailyVerseData.loadSavedState(7) } returns state
        every { DailyVerseData.getVersion(null) } returns version
        every { DailyVerseData.getAris(7, state, version, 1) } returns null
        val factory = DailyVerseFactory(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7))
        factory.onCreate()
        assertEquals(1, factory.getCount())
        factory.getViewAt(0)
        factory.getViewAt(0)
        factory.getViewAt(1)
        verify { anyConstructed<RemoteViews>().setTextViewText(R.id.text1, "") }
        verify(exactly = 1) { DailyVerseData.getAris(any(), any(), any(), any()) }
        verify(exactly = 0) { DailyVerseData.saveSavedState(any(), any()) }
        verify(exactly = 0) { anyConstructed<RemoteViews>().setOnClickFillInIntent(R.id.tReference, any()) }
    }
}
