package yuku.alkitab.base.widget

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.App
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.util.CurrentReading
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class LeftDrawerCurrentReadingTest {
    private lateinit var previousServices: AppServices
    private lateinit var db: InternalDb

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
        previousServices = App.services
        db = mockk(relaxed = true)
        every { db.listReadingPlanNames() } returns listOf("original")
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
    fun `a devotional passage appears in the drawer without a completion button`() {
        CurrentReading.setRanges(intArrayOf(0x350606, 0x35060a))
        val drawer = drawer()
        assertEquals(View.VISIBLE, drawer.panelCurrentReadingHeader.visibility)
        assertEquals("1 Timotius 6:6–10", drawer.bCurrentReadingReference.text.toString())
        assertEquals(View.GONE, drawer.bCurrentReadingComplete.visibility)
    }

    @Test
    fun `a plan passage exposes a working completion button`() {
        CurrentReading.setReadingPlan(0x350606, 0x35060a, "original", 12, 3)
        val drawer = drawer()
        val listener = mockk<LeftDrawer.Text.Listener>(relaxed = true)
        drawer.listener = listener
        assertEquals(View.VISIBLE, drawer.bCurrentReadingComplete.visibility)
        drawer.bCurrentReadingComplete.performClick()
        verify(exactly = 1) { listener.bCurrentReadingComplete_click() }
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
        assertEquals(View.GONE, drawer.bCurrentReadingComplete.visibility)
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
    }
}
