package yuku.alkitab.base.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.File
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.App
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.util.CurrentReading
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.util.IntArrayList

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LeftDrawerCurrentReadingTest {
    private lateinit var previousServices: AppServices
    private lateinit var db: InternalDb
    private val progress = mutableSetOf<Int>()

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
        previousServices = App.services
        db = mockk(relaxed = true)
        every { db.listReadingPlanNames() } returns listOf("original")
        every { db.getAllReadingCodesByReadingPlanProgressGid(any()) } answers {
            IntArrayList().also { codes -> progress.forEach(codes::add) }
        }
        every { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) } answers {
            progress.add(secondArg())
            Unit
        }
        every { db.deleteReadingPlanProgress(any(), any()) } answers {
            progress.remove(secondArg())
            Unit
        }
        val storage = mockk<StorageProvider>()
        every { storage.db } returns db
        val version = mockk<Version>()
        every { version.referenceRange(any(), any()) } returns "1 Timotius 6:6–10"
        val versions = mockk<VersionManager>()
        every { versions.activeVersion() } returns version
        App.services = AppServices(storage, versions, previousServices.uiDimensions)
        CurrentReading.clear()
    }

    @After
    fun tearDown() {
        CurrentReading.clear()
        App.services = previousServices
    }

    private fun drawer(): LeftDrawer.Text {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_Alkitab)
        return activity.layoutInflater.inflate(R.layout.left_drawer_text, null) as LeftDrawer.Text
    }

    private fun reference(drawer: LeftDrawer.Text, index: Int = 0): TextView =
        drawer.panelCurrentReadingRows.getChildAt(index).findViewById(R.id.bCurrentReadingReference)

    private fun checkbox(drawer: LeftDrawer.Text, index: Int = 0): CheckBox =
        drawer.panelCurrentReadingRows.getChildAt(index).findViewById(R.id.cCurrentReadingComplete)

    @Test
    fun `a devotional passage appears in the drawer without a completion checkbox`() {
        CurrentReading.setRanges(intArrayOf(0x350606, 0x35060a))
        val drawer = drawer()
        assertEquals(View.VISIBLE, drawer.findViewById<View>(R.id.dividerCurrentReading).visibility)
        assertEquals(View.VISIBLE, drawer.panelCurrentReadingHeader.visibility)
        assertEquals("1 Timotius 6:6–10", reference(drawer).text.toString())
        assertEquals(View.GONE, checkbox(drawer).visibility)
    }

    @Test
    fun `the checkbox ticks and unticks the plan reading without dismissing its reference`() {
        CurrentReading.setReadingPlan(intArrayOf(0x350606, 0x35060a), "original", 12)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        every { listener.cCurrentReadingComplete_checkedChange(any(), any()) } answers {
            CurrentReading.setPlanCompleted(secondArg(), firstArg())
            Unit
        }
        drawer.listener = listener
        assertEquals(View.VISIBLE, checkbox(drawer).visibility)
        assertFalse(checkbox(drawer).isChecked)
        checkbox(drawer).performClick()
        assertTrue(checkbox(drawer).isChecked)
        assertEquals(View.VISIBLE, drawer.panelCurrentReadingHeader.visibility)
        assertEquals(View.VISIBLE, reference(drawer).visibility)
        checkbox(drawer).performClick()
        assertFalse(checkbox(drawer).isChecked)
        assertEquals(View.VISIBLE, reference(drawer).visibility)
        verify(exactly = 1) { listener.cCurrentReadingComplete_checkedChange(0, true) }
        verify(exactly = 1) { listener.cCurrentReadingComplete_checkedChange(0, false) }
    }

    @Test
    fun `opening an already completed reading shows a tick without changing progress`() {
        progress.add((12 shl 8))
        CurrentReading.setReadingPlan(intArrayOf(0x350606, 0x35060a), "original", 12)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        drawer.listener = listener
        drawer.displayCurrentReading()
        assertTrue(checkbox(drawer).isChecked)
        verify(exactly = 0) { listener.cCurrentReadingComplete_checkedChange(any(), any()) }
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
    }

    @Test
    fun `a progress event refreshes the checkbox without invoking its user callback`() {
        CurrentReading.setReadingPlan(intArrayOf(0x350606, 0x35060a), "original", 12)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        drawer.listener = listener
        (drawer.context as AppCompatActivity).setContentView(drawer)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        progress.add((12 shl 8))
        AppEvents.emitReadingPlanProgressChanged()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(checkbox(drawer).isChecked)
        verify(exactly = 0) { listener.cCurrentReadingComplete_checkedChange(any(), any()) }
    }

    @Test
    fun `closing a passage removes its drawer reference without completing the plan`() {
        CurrentReading.setReadingPlan(intArrayOf(0x350606, 0x35060a), "original", 12)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        every { listener.bCurrentReadingClose_click() } answers { CurrentReading.clear() }
        drawer.listener = listener
        drawer.bCurrentReadingClose.performClick()
        drawer.displayCurrentReading()
        assertEquals(View.GONE, drawer.findViewById<View>(R.id.dividerCurrentReading).visibility)
        assertEquals(View.GONE, drawer.panelCurrentReadingHeader.visibility)
        assertEquals(View.GONE, drawer.panelCurrentReadingRows.visibility)
        assertEquals(0, drawer.panelCurrentReadingRows.childCount)
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
    }

    @Test
    fun `each daily reading has a separate reference and checkbox with one close button in the header`() {
        val ranges = intArrayOf(0x000100, 0x000300, 0x120101, 0x120106, 0x270101, 0x27010a)
        CurrentReading.setReadingPlan(ranges, "original", 12)
        val version = App.services.versions.activeVersion()
        every { version.referenceRange(ranges[0], ranges[1]) } returns "Genesis 1–3"
        every { version.referenceRange(ranges[2], ranges[3]) } returns "Psalms 1:1–6"
        every { version.referenceRange(ranges[4], ranges[5]) } returns "Matthew 1:1–10"
        progress.add((12 shl 8) or 1)
        val drawer = drawer()
        assertEquals(3, drawer.panelCurrentReadingRows.childCount)
        assertEquals("Genesis 1–3", reference(drawer, 0).text.toString())
        assertEquals("Psalms 1:1–6", reference(drawer, 1).text.toString())
        assertEquals("Matthew 1:1–10", reference(drawer, 2).text.toString())
        assertFalse(checkbox(drawer, 0).isChecked)
        assertTrue(checkbox(drawer, 1).isChecked)
        assertFalse(checkbox(drawer, 2).isChecked)
        assertEquals(null, drawer.panelCurrentReadingHeader.findViewById<CheckBox>(R.id.cCurrentReadingComplete))
        assertEquals(drawer.bCurrentReadingClose, drawer.panelCurrentReadingHeader.findViewById<View>(R.id.bCurrentReadingClose))
        for (index in 0..2) {
            val row = drawer.panelCurrentReadingRows.getChildAt(index)
            assertEquals(row, checkbox(drawer, index).parent)
            assertEquals(row, reference(drawer, index).parent)
            assertEquals(View.VISIBLE, checkbox(drawer, index).visibility)
            assertEquals(null, row.findViewById<View>(R.id.bCurrentReadingClose))
        }
    }

    @Test
    fun `toggling one daily row preserves other checkboxes and every active range`() {
        val ranges = intArrayOf(0x000100, 0x000300, 0x120101, 0x120106, 0x270101, 0x27010a)
        CurrentReading.setReadingPlan(ranges, "original", 12)
        progress.add((12 shl 8) or 0)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        every { listener.cCurrentReadingComplete_checkedChange(any(), any()) } answers {
            CurrentReading.setPlanCompleted(secondArg(), firstArg())
            Unit
        }
        drawer.listener = listener
        checkbox(drawer, 2).performClick()
        assertTrue(checkbox(drawer, 0).isChecked)
        assertFalse(checkbox(drawer, 1).isChecked)
        assertTrue(checkbox(drawer, 2).isChecked)
        assertArrayEquals(ranges, CurrentReading.getRanges())
        checkbox(drawer, 0).performClick()
        assertFalse(checkbox(drawer, 0).isChecked)
        assertTrue(checkbox(drawer, 2).isChecked)
        assertEquals(setOf((12 shl 8) or 2), progress)
        assertArrayEquals(ranges, CurrentReading.getRanges())
        assertEquals(View.VISIBLE, drawer.panelCurrentReadingHeader.visibility)
    }

    @Test
    fun `each reference row navigates to its own range`() {
        CurrentReading.setReadingPlan(intArrayOf(0x000100, 0x000300, 0x120101, 0x120106), "original", 12)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        drawer.listener = listener
        reference(drawer, 1).performClick()
        verify(exactly = 1) { listener.bCurrentReadingReference_click(1) }
        verify(exactly = 0) { listener.bCurrentReadingReference_click(0) }
    }

    @Test
    fun `the header close button clears all rows without erasing their completed progress`() {
        CurrentReading.setReadingPlan(intArrayOf(0x000100, 0x000300, 0x120101, 0x120106), "original", 12)
        progress.add((12 shl 8) or 1)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        every { listener.bCurrentReadingClose_click() } answers { CurrentReading.clear() }
        drawer.listener = listener
        drawer.bCurrentReadingClose.performClick()
        drawer.displayCurrentReading()
        assertEquals(0, drawer.panelCurrentReadingRows.childCount)
        assertEquals(View.GONE, drawer.panelCurrentReadingHeader.visibility)
        assertEquals(setOf((12 shl 8) or 1), progress)
        verify(exactly = 0) { db.deleteReadingPlanProgress(any(), any()) }
    }
    @Test
    fun `drawer header stays compact and visible controls share an optical center`() {
        CurrentReading.setReadingPlan(intArrayOf(0x000100, 0x000300, 0x120101, 0x120106), "original", 12)
        val drawer = drawer()
        drawer.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
        drawer.layout(0, 0, 320, 900)
        assertEquals(32, drawer.panelCurrentReadingHeader.height)
        val close = drawer.bCurrentReadingClose
        val check = checkbox(drawer, 0)
        fun opticalCenter(view: View): Float {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            var left = view.width
            var right = -1
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 128) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                }
            }
            assertTrue("Control must draw a visible glyph", right >= left)
            return view.left + (left + right + 1) / 2f
        }
        assertEquals(opticalCenter(close), opticalCenter(check), 1f)
        val headerTitle = (drawer.panelCurrentReadingHeader as android.view.ViewGroup).getChildAt(0) as TextView
        assertEquals(14f, headerTitle.textSize, 0f)
        val bitmap = Bitmap.createBitmap(drawer.width, drawer.height, Bitmap.Config.ARGB_8888)
        drawer.draw(Canvas(bitmap))
        val output = File("build/test-artifacts/reading-guide-previews/drawer.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `drawer scrolls behind system bars while its end items rest inside safe insets`() {
        val drawer = drawer()
        drawer.setPadding(13, 24, 17, 32)
        drawer.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY))
        drawer.layout(0, 0, 320, 420)
        fun topInDrawer(view: View): Int {
            var top = view.top
            var parent = view.parent as View
            while (parent !== drawer) {
                top += parent.top - parent.scrollY
                parent = parent.parent as View
            }
            return top - drawer.scrollY
        }
        fun pixel(y: Int): Int {
            val bitmap = Bitmap.createBitmap(drawer.width, drawer.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(-drawer.scrollX.toFloat(), -drawer.scrollY.toFloat())
            drawer.draw(canvas)
            return bitmap.getPixel(180, y)
        }
        val bible = drawer.findViewById<View>(R.id.bBible)
        val help = drawer.findViewById<View>(R.id.bHelp)
        assertEquals(24, topInDrawer(bible))
        drawer.scrollTo(0, 40)
        assertEquals(Color.rgb(55, 71, 79), pixel(12))
        drawer.scrollTo(0, 10_000)
        assertEquals(drawer.height - 32, topInDrawer(help) + help.height)
        drawer.scrollBy(0, -16)
        assertEquals(Color.rgb(55, 71, 79), pixel(drawer.height - 24))
        assertEquals(13, drawer.getChildAt(0).left)
        assertEquals(drawer.width - 17, drawer.getChildAt(0).right)
    }

}
