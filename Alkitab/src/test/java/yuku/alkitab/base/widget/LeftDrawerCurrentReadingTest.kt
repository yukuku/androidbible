package yuku.alkitab.base.widget

import android.view.View
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
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
@Config(application = yuku.afw.App::class, sdk = [34])
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

    @Test
    fun `a devotional passage appears in the drawer without a completion checkbox`() {
        CurrentReading.setRanges(intArrayOf(0x350606, 0x35060a))
        val drawer = drawer()
        assertEquals(View.VISIBLE, drawer.panelCurrentReadingHeader.visibility)
        assertEquals("1 Timotius 6:6–10", drawer.bCurrentReadingReference.text.toString())
        assertEquals(View.GONE, drawer.cCurrentReadingComplete.visibility)
    }

    @Test
    fun `the checkbox ticks and unticks the plan reading without dismissing its reference`() {
        CurrentReading.setReadingPlan(0x350606, 0x35060a, "original", 12, 3)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        every { listener.cCurrentReadingComplete_checkedChange(any()) } answers {
            CurrentReading.setPlanCompleted(firstArg())
            Unit
        }
        drawer.listener = listener
        assertEquals(View.VISIBLE, drawer.cCurrentReadingComplete.visibility)
        assertFalse(drawer.cCurrentReadingComplete.isChecked)
        drawer.cCurrentReadingComplete.performClick()
        assertTrue(drawer.cCurrentReadingComplete.isChecked)
        assertEquals(View.VISIBLE, drawer.panelCurrentReadingHeader.visibility)
        assertEquals(View.VISIBLE, drawer.bCurrentReadingReference.visibility)
        drawer.cCurrentReadingComplete.performClick()
        assertFalse(drawer.cCurrentReadingComplete.isChecked)
        assertEquals(View.VISIBLE, drawer.bCurrentReadingReference.visibility)
        verify(exactly = 1) { listener.cCurrentReadingComplete_checkedChange(true) }
        verify(exactly = 1) { listener.cCurrentReadingComplete_checkedChange(false) }
    }

    @Test
    fun `opening an already completed reading shows a tick without changing progress`() {
        progress.add((12 shl 8) or 3)
        CurrentReading.setReadingPlan(0x350606, 0x35060a, "original", 12, 3)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        drawer.listener = listener
        drawer.displayCurrentReading()
        assertTrue(drawer.cCurrentReadingComplete.isChecked)
        verify(exactly = 0) { listener.cCurrentReadingComplete_checkedChange(any()) }
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
    }

    @Test
    fun `a progress event refreshes the checkbox without invoking its user callback`() {
        CurrentReading.setReadingPlan(0x350606, 0x35060a, "original", 12, 3)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        drawer.listener = listener
        (drawer.context as AppCompatActivity).setContentView(drawer)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        progress.add((12 shl 8) or 3)
        AppEvents.emitReadingPlanProgressChanged()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(drawer.cCurrentReadingComplete.isChecked)
        verify(exactly = 0) { listener.cCurrentReadingComplete_checkedChange(any()) }
    }

    @Test
    fun `closing a passage removes its drawer reference without completing the plan`() {
        CurrentReading.setReadingPlan(0x350606, 0x35060a, "original", 12, 3)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        every { listener.bCurrentReadingClose_click() } answers { CurrentReading.clear() }
        drawer.listener = listener
        drawer.bCurrentReadingClose.performClick()
        drawer.displayCurrentReading()
        assertEquals(View.GONE, drawer.panelCurrentReadingHeader.visibility)
        assertEquals(View.GONE, drawer.bCurrentReadingReference.visibility)
        assertEquals(View.GONE, drawer.cCurrentReadingComplete.visibility)
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
    }
}
