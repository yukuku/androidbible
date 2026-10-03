package yuku.alkitab.base.ac

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import io.mockk.every
import io.mockk.mockk
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
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
import yuku.alkitab.base.ac.DevotionActivity.DevotionKind
import yuku.alkitab.base.devotion.DevotionDownloader
import yuku.alkitab.base.devotion.ManualExecutor
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.services.UiDimensionsProvider
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "en-rUS")
@LooperMode(LooperMode.Mode.PAUSED)
class DevotionActivityTest {
    class TestActivity : DevotionActivity() {
        override fun getDownloader() = testDownloader
        override fun startPrefetch(kind: DevotionKind) {}
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_Alkitab)
            super.onCreate(savedInstanceState)
        }
    }
    companion object { lateinit var testDownloader: DevotionDownloader }
    private val foreground = ManualExecutor()
    private val background = ManualExecutor()
    private lateinit var helper: InternalDbHelper
    private lateinit var db: InternalDb
    private lateinit var originalServices: AppServices
    private lateinit var controller: ActivityController<TestActivity>
    private val format = SimpleDateFormat("yyyyMMdd", Locale.US)
    private var output = "<h2>Morning</h2><p>Morning text</p><h2>Evening</h2><p>Evening text</p>"
    private var onExecute: () -> Unit = {}
    private var result = DevotionDownloader.State.READY
    private val requests = mutableListOf<DevotionDownloader.Key>()

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        Preferences.invalidate()
        Preferences.setString(Prefkey.devotion_last_kind_name, "me-en")
        Preferences.setString(app.getString(R.string.pref_language_key), "en")
        helper = InternalDbHelper(app)
        db = InternalDb(helper)
        originalServices = App.services
        val storage = mockk<StorageProvider> { every { db } returns this@DevotionActivityTest.db }
        val dimensions = S.CalculatedDimensions().apply {
            backgroundColor = Color.BLACK
            fontColor = Color.WHITE
            fontSize2dp = 18f
            lineSpacingMult = 1f
        }
        val ui = mockk<UiDimensionsProvider> { every { applied() } returns dimensions }
        App.services = AppServices(storage, mockk(), ui)
        testDownloader = DevotionDownloader({ key, _ ->
            requests.add(key)
            object : DevotionDownloader.Request {
                override fun cancel() {}
                override fun execute(): DevotionDownloader.State {
                    onExecute()
                    if (result == DevotionDownloader.State.READY || result == DevotionDownloader.State.UNAVAILABLE) {
                        db.storeArticleToDevotions(DevotionKind.getByName(key.name)!!.getArticle(key.date).apply {
                            fillIn(if (result == DevotionDownloader.State.UNAVAILABLE) "NG" else output)
                        })
                    }
                    return result
                }
            }
        }, foreground, background)
        controller = Robolectric.buildActivity(TestActivity::class.java).create(savedSelection()).start().resume().visible()
        idle()
    }

    private fun savedSelection() = Bundle().apply {
        putString("devotionKind", "me-en")
        putLong("devotionDate", format.parse("20260929")!!.time)
    }
    private val activity get() = controller.get()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun status() = activity.findViewById<TextView>(R.id.lDownloadStatus).text.toString()
    private fun visible(id: Int) = activity.findViewById<View>(id).visibility == View.VISIBLE

    @After fun tearDown() {
        controller.pause().stop().destroy()
        testDownloader.shutdown()
        App.services = originalServices
        helper.close()
    }

    @Test fun `waiting failure and Retry button lead to the selected complete reading`() {
        assertEquals(activity.getString(R.string.devotion_download_queued), status())
        assertTrue(visible(R.id.downloadProgress))
        assertFalse(visible(R.id.bRetry))
        result = DevotionDownloader.State.FAILED
        foreground.runNext()
        idle()
        assertEquals(activity.getString(R.string.devotion_download_failed), status())
        assertFalse(visible(R.id.downloadProgress))
        assertTrue(visible(R.id.bRetry))
        activity.findViewById<View>(R.id.bRetry).performClick()
        assertEquals(activity.getString(R.string.devotion_download_queued), status())
        result = DevotionDownloader.State.READY
        foreground.runNext()
        idle()
        assertTrue(activity.lContent.text.contains("Morning text"))
        assertTrue(activity.lContent.text.contains("Evening text"))
        assertFalse(visible(R.id.downloadStatus))
        assertEquals(listOf("20260929", "20260929"), requests.map { it.date })
    }

    @Test fun `an active download displays downloading rather than waiting`() {
        onExecute = {
            idle()
            assertEquals(activity.getString(R.string.devotion_downloading), status())
            assertTrue(visible(R.id.downloadProgress))
            assertFalse(visible(R.id.bRetry))
        }
        foreground.runNext()
        idle()
        assertFalse(visible(R.id.downloadStatus))
    }

    @Test fun `a failed refresh keeps the cached reading visible alongside Retry`() {
        foreground.runNext()
        idle()
        val cachedContent = activity.lContent.text.toString()
        activity.bReload_click()
        result = DevotionDownloader.State.FAILED
        foreground.runNext()
        idle()
        assertTrue(visible(R.id.lContent))
        assertTrue(visible(R.id.bRetry))
        assertEquals(cachedContent, activity.lContent.text.toString())
    }

    @Test fun `status and Retry use the app language when it differs from the device`() {
        controller.pause().stop().destroy()
        Preferences.setString(RuntimeEnvironment.getApplication().getString(R.string.pref_language_key), "in")
        controller = Robolectric.buildActivity(TestActivity::class.java).create(savedSelection()).start().resume().visible()
        result = DevotionDownloader.State.FAILED
        foreground.runNext()
        idle()
        assertEquals("Renungan ini tidak dapat diunduh. Periksa koneksi Anda dan coba lagi.", status())
        assertEquals("Coba lagi", activity.bRetry.text.toString())
    }

    @Test fun `unavailable content has a distinct message and retry hides the button while loading`() {
        result = DevotionDownloader.State.UNAVAILABLE
        foreground.runNext()
        idle()
        assertEquals(activity.getString(R.string.devotion_unavailable), status())
        assertTrue(visible(R.id.bRetry))
        assertFalse(visible(R.id.lContent))
        activity.findViewById<View>(R.id.bRetry).performClick()
        assertFalse(visible(R.id.bRetry))
        result = DevotionDownloader.State.READY
        foreground.runNext()
        idle()
        assertTrue(visible(R.id.lContent))
    }

    @Test fun `a completion while stopped is restored on start without a duplicate download`() {
        controller.pause().stop()
        foreground.runNext()
        controller.start().resume()
        idle()
        assertTrue(activity.lContent.text.contains("Evening text"))
        assertFalse(visible(R.id.downloadStatus))
        assertEquals(1, requests.size)
    }

    @Test fun `failure while stopped is visible with Retry on restart`() {
        controller.pause().stop()
        result = DevotionDownloader.State.FAILED
        foreground.runNext()
        controller.start().resume()
        idle()
        assertTrue(visible(R.id.bRetry))
        assertEquals(activity.getString(R.string.devotion_download_failed), status())
        assertEquals(1, requests.size)
    }

    @Test fun `late results from other dates and sources cannot replace the selection`() {
        activity.bPrev_click()
        activity.bPrev_click()
        activity.cbKind_itemSelected(DevotionKind.MEID_A)
        activity.currentDate = format.parse("20260925")
        activity.display()
        foreground.runAll()
        idle()
        assertEquals("20260925", format.format(activity.currentDate))
        assertEquals(DevotionKind.MEID_A, activity.currentKind)
        val selectedContent = activity.lContent.text.toString()
        output = "<p>Stale background content</p>"
        background.runAll()
        idle()
        assertEquals(selectedContent, activity.lContent.text.toString())
        assertFalse(activity.lContent.text.contains("Stale"))
    }

    @Test fun `recreation during an outstanding request reuses that request and receives its result`() {
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop().destroy()
        controller = Robolectric.buildActivity(TestActivity::class.java).create(saved).start().resume().visible()
        idle()
        assertEquals(1, foreground.size)
        foreground.runNext()
        idle()
        assertTrue(activity.lContent.text.contains("Evening text"))
        assertEquals(1, requests.size)
    }

    @Test fun `recreation restores source date and offline cache in the reader colors`() {
        foreground.runNext()
        idle()
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop().destroy()
        controller = Robolectric.buildActivity(TestActivity::class.java).create(saved).start().resume().visible()
        idle()
        assertEquals(DevotionKind.ME_EN, activity.currentKind)
        assertEquals("20260929", format.format(activity.currentDate))
        assertTrue(activity.lContent.text.contains("Morning text"))
        assertEquals(Color.WHITE, activity.lDownloadStatus.currentTextColor)
        assertEquals(Color.WHITE, activity.lContent.currentTextColor)
        assertEquals(1, requests.size)
    }
}
