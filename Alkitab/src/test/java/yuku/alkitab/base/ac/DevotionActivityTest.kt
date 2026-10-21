package yuku.alkitab.base.ac

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.text.Selection
import android.text.Spannable
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.progressindicator.CircularProgressIndicator
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileOutputStream
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.ac.DevotionActivity.DevotionKind
import yuku.alkitab.base.devotion.DevotionDownloader
import yuku.alkitab.base.devotion.ManualDispatcher
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.services.UiDimensionsProvider
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.CurrentReading
import yuku.alkitab.base.widget.CallbackSpan
import yuku.alkitab.base.widget.TwofingerLinearLayout
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version
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
    private val foreground = ManualDispatcher()
    private val background = ManualDispatcher()
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
        val ui = mockk<UiDimensionsProvider> {
            every { applied() } returns dimensions
            every { recalculate() } returns Unit
        }
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

    @Test fun `display drawer item opens an inline panel and back dismisses it without leaving the article`() {
        val overlay = activity.findViewById<FrameLayout>(R.id.textAppearanceOverlay)
        val initialChildCount = overlay.childCount
        val content = activity.lContent.text
        activity.findViewById<View>(R.id.bDisplay).performClick()
        assertEquals(initialChildCount + 1, overlay.childCount)
        assertTrue(overlay.getChildAt(initialChildCount) is ComposeView)
        assertSame(content, activity.lContent.text)

        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(initialChildCount, overlay.childCount)
        assertFalse(activity.isFinishing)
        assertSame(content, activity.lContent.text)

        activity.findViewById<View>(R.id.bDisplay).performClick()
        assertEquals(initialChildCount + 1, overlay.childCount)
        activity.findViewById<View>(R.id.bDisplay).performClick()
        assertEquals(initialChildCount, overlay.childCount)
    }

    @Test fun `pinches resize the article cumulatively within the song zoom limits`() {
        foreground.runNext()
        idle()
        val initialSize = activity.lContent.textSize
        val content = activity.lContent.text
        pinch(1.235f)
        assertEquals(initialSize * 1.235f, activity.lContent.textSize, 0.01f)
        pinch(1.125f)
        assertEquals(initialSize * 1.389375f, activity.lContent.textSize, 0.01f)
        pinch(3f)
        assertEquals(initialSize * 2f, activity.lContent.textSize, 0.01f)
        pinch(0.1f)
        assertEquals(initialSize * 0.5f, activity.lContent.textSize, 0.01f)
        assertSame(content, activity.lContent.text)
        assertEquals(18f, App.services.uiDimensions.applied().fontSize2dp, 0f)
    }

    @Test fun `temporary zoom survives reading changes and returning with updated reader settings`() {
        foreground.runNext()
        idle()
        val initialSize = activity.lContent.textSize
        pinch(1.5f)
        activity.bNext_click()
        foreground.runNext()
        idle()
        assertEquals(initialSize * 1.5f, activity.lContent.textSize, 0.01f)
        activity.cbKind_itemSelected(DevotionKind.RH)
        foreground.runNext()
        idle()
        assertEquals(initialSize * 1.5f, activity.lContent.textSize, 0.01f)
        controller.pause().stop()
        App.services.uiDimensions.applied().fontSize2dp = 22f
        controller.start().resume().visible()
        idle()
        val density = activity.resources.displayMetrics.density
        assertEquals(22f * density * 1.5f, activity.lContent.textSize, 0.01f)
    }

    @Test fun `a newly created reader resets temporary zoom without changing saved appearance`() {
        foreground.runNext()
        idle()
        val initialSize = activity.lContent.textSize
        val storedPreferences = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
        val preferences = storedPreferences.all.toMap()
        pinch(1.5f)
        assertEquals(preferences, storedPreferences.all)
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop().destroy()
        controller = Robolectric.buildActivity(TestActivity::class.java).create(saved).start().resume().visible()
        idle()
        assertEquals(initialSize, activity.lContent.textSize, 0.01f)
        assertEquals(preferences, storedPreferences.all)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-rUS-w393dp-h851dp-mdpi")
    fun `zoomed devotional text reflows within the reader width`() {
        output = "<h2>Morning</h2><p>" + "Begin this day with confidence in the strength that God supplies. ".repeat(20) + "</p>"
        foreground.runNext()
        idle()
        val view = activity.window.decorView
        fun capture(name: String) {
            idle()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(851, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 393, 851)
            val bitmap = Bitmap.createBitmap(393, 851, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            saveScreenshot(bitmap, name)
        }
        capture("pinch-normal.png")
        val originalWidth = activity.lContent.width
        val originalLines = activity.lContent.lineCount
        pinch(2f)
        capture("pinch-zoomed.png")
        assertEquals(originalWidth, activity.lContent.width)
        assertTrue(activity.lContent.lineCount > originalLines)
        assertEquals(36f, activity.lContent.textSize, 0.01f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-rUS-w393dp-h851dp-mdpi")
    fun `a left swipe on the article advances exactly one day and a right swipe goes back`() {
        output = "<p>" + "Devotional text for scrolling. ".repeat(400) + "</p>"
        foreground.runNext()
        idle()
        swipe(-160f, 56f, childDisallowsInterception = true)
        assertEquals("20260930", format.format(activity.currentDate))
        foreground.runNext()
        idle()
        swipe(160f, 56f, childDisallowsInterception = true)
        assertEquals("20260929", format.format(activity.currentDate))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-rUS-w393dp-h851dp-mdpi")
    fun `vertical scrolling and small horizontal movements keep the selected day`() {
        output = "<p>" + "Devotional text for scrolling. ".repeat(400) + "</p>"
        foreground.runNext()
        idle()
        swipe(0f, -160f)
        assertEquals("20260929", format.format(activity.currentDate))
        assertTrue(activity.findViewById<View>(R.id.scrollContent).scrollY > 0)
        swipe(-20f)
        assertEquals("20260929", format.format(activity.currentDate))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-rUS-w393dp-h851dp-mdpi")
    fun `swipes change days while the article is loading or unavailable`() {
        swipe(-160f)
        assertEquals("20260930", format.format(activity.currentDate))
        result = DevotionDownloader.State.UNAVAILABLE
        foreground.runAll()
        idle()
        swipe(160f)
        assertEquals("20260929", format.format(activity.currentDate))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-rUS-w393dp-h851dp-mdpi")
    fun `swiping selected article text keeps the selected day`() {
        output = "<p>" + "Devotional text for selection. ".repeat(400) + "</p>"
        foreground.runNext()
        idle()
        layoutReader()
        val text = activity.lContent.text as Spannable
        Selection.setSelection(text, 0, 10)
        swipe(-160f, childDisallowsInterception = true)
        assertEquals("20260929", format.format(activity.currentDate))
        Selection.removeSelection(text)
        swipe(-160f)
        assertEquals("20260930", format.format(activity.currentDate))
    }

    private fun layoutReader() {
        val decor = activity.window.decorView
        val density = activity.resources.displayMetrics.density
        decor.measure(
            View.MeasureSpec.makeMeasureSpec((393 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((851 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, (393 * density).toInt(), (851 * density).toInt())
    }

    private fun swipe(dx: Float, dy: Float = 0f, childDisallowsInterception: Boolean = false) {
        layoutReader()
        val density = activity.resources.displayMetrics.density
        val root = activity.findViewById<TwofingerLinearLayout>(R.id.root)
        val downTime = android.os.SystemClock.uptimeMillis()
        fun touch(action: Int, fraction: Float) {
            val event = MotionEvent.obtain(downTime, downTime + (fraction * 300).toLong(), action,
                (196f + dx * fraction) * density, (300f + dy * fraction) * density, 0)
            try {
                root.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        touch(MotionEvent.ACTION_DOWN, 0f)
        if (childDisallowsInterception) activity.lContent.parent.requestDisallowInterceptTouchEvent(true)
        for (step in 1..64) touch(MotionEvent.ACTION_MOVE, step / 64f)
        touch(MotionEvent.ACTION_UP, 1f)
        idle()
    }

    private fun pinch(scale: Float) {
        val root = activity.findViewById<TwofingerLinearLayout>(R.id.root)
        val density = activity.resources.displayMetrics.density
        val downTime = android.os.SystemClock.uptimeMillis()
        var time = downTime
        fun touch(action: Int, span: Float, pointerCount: Int) {
            val properties = Array(pointerCount) { index -> MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            val coordinates = Array(pointerCount) { index -> MotionEvent.PointerCoords().apply {
                x = (200f + if (index == 0) -span / 2f else span / 2f) * density
                y = 300f * density
                pressure = 1f
                size = 1f
            } }
            time += 16
            val event = MotionEvent.obtain(downTime, time, action, pointerCount, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            try {
                root.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        touch(MotionEvent.ACTION_DOWN, 100f, 1)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 100f, 2)
        touch(MotionEvent.ACTION_MOVE, 100f, 2)
        touch(MotionEvent.ACTION_MOVE, 200f, 2)
        touch(MotionEvent.ACTION_MOVE, 200f * scale, 2)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 200f * scale, 2)
        touch(MotionEvent.ACTION_UP, 200f * scale, 1)
    }

    @Test fun `devotional links retain disjoint reading guides in the new downloader activity`() {
        val version = mockk<Version>()
        every { App.services.versions.activeVersion() } returns version
        every { version.getBook(40) } returns Book().apply {
            bookId = 40
            chapter_count = 16
            verse_counts = IntArray(16) { 50 }
        }
        val listener = ReflectionHelpers.getField<CallbackSpan.OnClickListener<String>>(activity, "verseClickListener")
        listener.onClick(activity.findViewById(R.id.lContent), "Markus 9:5-6,14-23")
        try {
            assertArrayEquals(intArrayOf(0x280905, 0x280906, 0x28090e, 0x280917), CurrentReading.getRanges())
            assertEquals(0x280905, shadowOf(activity).nextStartedActivity.getIntExtra("ari", -1))
            assertEquals(activity.getString(R.string.devotion_download_queued), status())
            foreground.runNext()
            idle()
            assertTrue(activity.findViewById<TextView>(R.id.lContent).text.contains("Morning text"))
            assertArrayEquals(intArrayOf(0x280905, 0x280906, 0x28090e, 0x280917), CurrentReading.getRanges())
        } finally {
            CurrentReading.clear()
        }
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
        assertEquals("Gagal mengunduh renungan. Periksa koneksi Anda dan coba lagi.", status())
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
        activity.currentDate = format.parse("20260925")!!
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
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-rUS-w393dp-h851dp-mdpi")
    fun `capture the devotional screens in light mode`() = captureScreens(false)

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-rUS-w393dp-h851dp-mdpi")
    fun `capture the devotional screens in dark mode`() = captureScreens(true)

    private fun captureScreens(dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        val dimensions = App.services.uiDimensions.applied()
        dimensions.backgroundColor = if (dark) Color.BLACK else Color.WHITE
        dimensions.fontColor = if (dark) Color.WHITE else Color.BLACK
        Preferences.setBoolean(Prefkey.is_night_mode, dark)
        controller.pause().stop().start().resume().visible()
        idle()

        val screenshots = linkedMapOf<String, Bitmap>()
        fun capture(state: String) {
            idle()
            val view = activity.window.decorView
            val width = 393
            val height = 851
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
            if (state != "reading") {
                val statusBounds = android.graphics.Rect()
                val viewportBounds = android.graphics.Rect()
                activity.findViewById<View>(R.id.downloadStatus).getGlobalVisibleRect(statusBounds)
                activity.findViewById<View>(R.id.scrollContent).getGlobalVisibleRect(viewportBounds)
                assertEquals(viewportBounds, statusBounds)
                val children = listOf(R.id.downloadProgress, R.id.lDownloadStatus, R.id.bRetry)
                    .map { activity.findViewById<View>(it) }.filter { it.visibility == View.VISIBLE }
                    .map { child -> android.graphics.Rect().also { child.getGlobalVisibleRect(it) } }
                val center = (children.minOf { it.top } + children.maxOf { it.bottom }) / 2f
                assertEquals(viewportBounds.exactCenterY(), center, 16f)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            // Native snapshots pin the indeterminate animation rather than depending on frame timing.
            val progress = activity.findViewById<CircularProgressIndicator>(R.id.downloadProgress)
            if (progress.visibility == View.VISIBLE) {
                val delegate = ReflectionHelpers.getField<Any>(progress.indeterminateDrawable, "animatorDelegate")
                ReflectionHelpers.callInstanceMethod<Unit>(
                    delegate, "setAnimationFraction",
                    ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType, 0.1f),
                )
            }
            view.draw(Canvas(bitmap))
            screenshots[state] = bitmap
            saveScreenshot(bitmap, "$theme-$state.png")
        }

        capture("queued")
        onExecute = { capture("downloading") }
        result = DevotionDownloader.State.FAILED
        foreground.runNext()
        idle()
        assertEquals(activity.getString(R.string.devotion_download_failed), status())
        capture("failed")

        onExecute = {}
        activity.bRetry.performClick()
        result = DevotionDownloader.State.UNAVAILABLE
        foreground.runNext()
        idle()
        assertEquals(activity.getString(R.string.devotion_unavailable), status())
        capture("unavailable")

        output = """
            <h2>Morning</h2>
            <p><i>“My grace is sufficient for thee.”</i><br/>2 Corinthians 12:9</p>
            <p>Begin this day with confidence in the strength that God supplies.
            Bring your cares to him in prayer, and trust him with the work before you.</p>
            <h2>Evening</h2>
            <p><i>“The Lord is my shepherd; I shall not want.”</i><br/>Psalm 23:1</p>
            <p>As the day draws to a close, remember the care of the Shepherd.
            Give thanks for his provision and rest in his peace.</p>
        """.trimIndent()
        activity.bRetry.performClick()
        result = DevotionDownloader.State.READY
        foreground.runNext()
        idle()
        assertTrue(visible(R.id.lContent))
        capture("reading")
        assertEquals(5, screenshots.size)

        val strip = Bitmap.createBitmap(393 * screenshots.size, 891, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        canvas.drawColor(if (dark) Color.BLACK else Color.WHITE)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.WHITE else Color.BLACK
            textSize = 18f
        }
        screenshots.entries.forEachIndexed { index, (state, bitmap) ->
            canvas.drawText("$theme / $state", index * 393f + 16f, 26f, label)
            canvas.drawBitmap(bitmap, null, RectF(index * 393f, 40f, (index + 1) * 393f, 891f), null)
        }
        saveScreenshot(strip, "$theme-overview.png")
    }

    @Test
    fun `Indonesian app language uses full weekday and abbreviated month even on an English device`() {
        Preferences.setString(activity.getString(R.string.pref_language_key), "in")
        activity.currentDate = format.parse("20261018")!!
        activity.display()
        assertEquals("Minggu, 18 Okt 2026", activity.supportActionBar!!.subtitle.toString())
        activity.currentDate = format.parse("20261004")!!
        activity.display()
        assertEquals("Minggu, 4 Okt 2026", activity.supportActionBar!!.subtitle.toString())
    }

    @Test
    fun `English app language keeps the existing device date format`() {
        activity.currentDate = format.parse("20261018")!!
        activity.display()
        assertEquals(activity.getString(R.string.hari_minggu) + ", " + android.text.format.DateFormat.getDateFormat(activity).format(activity.currentDate),
            activity.supportActionBar!!.subtitle.toString())
    }

    @Test
    fun `day navigation follows calendar dates across daylight saving changes`() {
        val originalZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            val calendar = java.util.Calendar.getInstance().apply {
                clear()
                set(2026, java.util.Calendar.NOVEMBER, 1, 0, 30)
            }
            activity.currentDate = calendar.time
            activity.bNext_click()
            calendar.time = activity.currentDate
            assertEquals(2, calendar.get(java.util.Calendar.DAY_OF_MONTH))
            assertEquals(0, calendar.get(java.util.Calendar.HOUR_OF_DAY))
            activity.bPrev_click()
            calendar.time = activity.currentDate
            assertEquals(1, calendar.get(java.util.Calendar.DAY_OF_MONTH))
        } finally {
            java.util.TimeZone.setDefault(originalZone)
        }
    }

    private fun saveScreenshot(bitmap: Bitmap, name: String) {
        val directory = File(System.getProperty("user.dir"), "build/snapshots/devotions")
        check(directory.isDirectory || directory.mkdirs())
        FileOutputStream(File(directory, name)).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

}
