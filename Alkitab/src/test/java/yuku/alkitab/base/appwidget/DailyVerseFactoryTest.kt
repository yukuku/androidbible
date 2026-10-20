package yuku.alkitab.base.appwidget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.TextView
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DailyVerseFactoryTest {
    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        Preferences.invalidate()
        Preferences.setString(R.string.pref_language_key, "en")
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
        Preferences.remove(yuku.afw.App.context.getString(R.string.pref_language_key))
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
        every { DailyVerseData.resolveVersion("first") } returns DailyVerseData.ResolvedVersion(version, isFallback = false)
        every { DailyVerseData.getAris(7, state, version, 1) } returns intArrayOf(ari)
        every { version.referenceWithVerseCount(ari, 1) } returns "Genesis 1:1"
        every { version.loadVerseText(ari) } returns "First verse"
        val factory = DailyVerseFactory(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7))
        factory.onCreate()

        every { DailyVerseData.loadSavedState(7) } returns nextState
        every { DailyVerseData.resolveVersion("second") } returns DailyVerseData.ResolvedVersion(nextVersion, isFallback = false)
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
        val state = DailyVerseData.SavedState().apply { versionId = "internal" }
        val version = mockk<Version>()
        every { DailyVerseData.loadSavedState(7) } returns state
        every { DailyVerseData.resolveVersion("internal") } returns DailyVerseData.ResolvedVersion(version, isFallback = false)
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

    @Test
    fun `fallback notice offers settings for its widget even without an available passage`() {
        val state = DailyVerseData.SavedState().apply {
            versionId = "missing"
            hideAppIcon = true
        }
        val version = mockk<Version>()
        every { version.longName } returns "Internal Bible"
        every { DailyVerseData.loadSavedState(7) } returns state
        every { DailyVerseData.resolveVersion("missing") } returns DailyVerseData.ResolvedVersion(version, isFallback = true)
        every { DailyVerseData.getAris(7, state, version, 1) } returns null
        val factory = DailyVerseFactory(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7))
        factory.onCreate()
        factory.getViewAt(0)

        verify { anyConstructed<RemoteViews>().setViewVisibility(R.id.imgLogo, View.GONE) }
        verify { anyConstructed<RemoteViews>().setViewVisibility(R.id.tVersionFallback, View.VISIBLE) }
        verify {
            anyConstructed<RemoteViews>().setOnClickFillInIntent(R.id.tVersionFallback, match {
                it.getStringExtra("app_widget_action") == "configure_widget" &&
                    it.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0) == 7
            })
        }
    }

    @Test
    fun `fallback notice renders with widget appearance and disappears when the version is available`() {
        unmockkConstructor(RemoteViews::class)
        val context = RuntimeEnvironment.getApplication()
        val state = DailyVerseData.SavedState().apply {
            versionId = "missing"
            textSize = 18f
            darkText = true
        }
        val version = mockk<Version>()
        every { version.longName } returns "Internal Bible"
        every { version.referenceWithVerseCount(0x000101, 1) } returns "Genesis 1:1"
        every { DailyVerseData.loadSavedState(7) } returns state
        every { DailyVerseData.resolveVersion("missing") } returns DailyVerseData.ResolvedVersion(version, isFallback = true)
        every { DailyVerseData.getAris(7, state, version, 1) } returns intArrayOf(0x000101)
        val factory = DailyVerseFactory(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7))
        factory.onCreate()
        val header = factory.getViewAt(0).apply(context, null)
        val notice = header.findViewById<TextView>(R.id.tVersionFallback)
        assertEquals(View.VISIBLE, notice.visibility)
        assertEquals("Selected version unavailable. Using Internal Bible. Tap to change.", notice.text.toString())
        assertEquals(Color.BLACK, notice.currentTextColor)
        assertEquals(18f * context.resources.displayMetrics.scaledDensity, notice.textSize, 0.01f)
        assertEquals("Genesis 1:1", header.findViewById<TextView>(R.id.tReference).text.toString())

        Preferences.setString(R.string.pref_language_key, "in")
        factory.getViewAt(0).reapply(context, header)
        assertEquals("Versi yang dipilih tidak tersedia. Menggunakan Internal Bible. Ketuk untuk mengganti.", notice.text.toString())

        state.versionId = null
        state.darkText = false
        every { DailyVerseData.resolveVersion(null) } returns DailyVerseData.ResolvedVersion(version, isFallback = true)
        factory.onDataSetChanged()
        factory.getViewAt(0).reapply(context, header)
        assertEquals("Menggunakan Internal Bible. Ketuk untuk memilih versi Alkitab.", notice.text.toString())
        assertEquals(Color.WHITE, notice.currentTextColor)

        state.versionId = "missing"
        every { DailyVerseData.resolveVersion("missing") } returns DailyVerseData.ResolvedVersion(version, isFallback = false)
        factory.onDataSetChanged()
        factory.getViewAt(0).reapply(context, header)
        assertEquals(View.GONE, notice.visibility)
        verify(exactly = 0) { DailyVerseData.saveSavedState(any(), any()) }
    }
}
