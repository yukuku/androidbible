package yuku.alkitab.base.ac

import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import yuku.alkitab.base.App
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.debug.R
import yuku.alkitab.util.IntArrayList
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.base.util.CurrentReading

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class ReadingPlanCurrentReadingTest {
    @After
    fun tearDown() = CurrentReading.clear()

    @Test
    fun `detail references select their own day without marking progress and recycled rows remove extra passages`() {
        val services = App.services
        try {
            App.services = AppServices(mockk(), mockk {
                every { activeVersion() } returns mockk {
                    every { referenceRange(any(), any()) } returns "Genesis 1"
                }
            }, mockk())
            val activity = Robolectric.buildActivity(ReadingPlanActivity::class.java).get()
            activity.setTheme(R.style.Theme_Alkitab)
            val day = intArrayOf(0x000100, 0x000300, 0x120101, 0x120106)
            activity.readingPlan = ReadingPlan().apply {
                info.name = "plan-a"
                info.duration = 3
                dailyVerses = arrayOf(intArrayOf(0x020100, 0x020200), day, intArrayOf(0x030100, 0x030200))
            }
            activity.readReadingCodes = IntArrayList()
            activity.dayNumber = 0
            activity.showDetails = true
            val adapter = activity.ReadingPlanAdapter()
            adapter.load()
            val parent = LinearLayout(activity)
            val row = adapter.newView(3, parent) as LinearLayout
            adapter.bindView(row, 3, parent)
            val passage = row.findViewWithTag<LinearLayout>(1)
            assertTrue(passage.getChildAt(1) is CheckBox)
            assertFalse(passage.findViewById<CheckBox>(R.id.checkbox).isChecked)
            passage.findViewById<Button>(R.id.bReference).performClick()
            assertArrayEquals(day, CurrentReading.getRanges())
            assertEquals(CurrentReading.Plan("plan-a", 1), CurrentReading.getPlan())
            assertEquals(day[2], Shadows.shadowOf(activity).nextStartedActivity.getIntExtra("ari", -1))
            assertEquals(0, activity.readReadingCodes.size())
            adapter.bindView(row, 4, parent)
            assertEquals(2, row.childCount)
            row.findViewWithTag<LinearLayout>(0).findViewById<Button>(R.id.bReference).performClick()
            assertEquals(CurrentReading.Plan("plan-a", 2), CurrentReading.getPlan())
        } finally {
            App.services = services
        }
    }

    @Test
    fun `opening a later passage activates the entire selected day and navigates to that passage`() {
        val day = intArrayOf(0x000100, 0x000300, 0x120101, 0x120106, 0x270101, 0x27010a)
        val activity = Robolectric.buildActivity(ReadingPlanActivity::class.java).get()
        activity.readingPlan = ReadingPlan().apply {
            info.name = "plan-a"
            dailyVerses = arrayOf(intArrayOf(0x020100, 0x020200), day)
        }
        activity.goToIsiActivity(1, 2)
        assertArrayEquals(day, CurrentReading.getRanges())
        assertEquals(CurrentReading.Plan("plan-a", 1), CurrentReading.getPlan())
        assertEquals(day[4], Shadows.shadowOf(activity).nextStartedActivity.getIntExtra("ari", -1))
    }

    @Test
    fun `opening a day with one passage retains a single range`() {
        val day = intArrayOf(0x000100, 0x000300)
        val activity = Robolectric.buildActivity(ReadingPlanActivity::class.java).get()
        activity.readingPlan = ReadingPlan().apply {
            info.name = "plan-a"
            dailyVerses = arrayOf(day)
        }
        activity.goToIsiActivity(0, 0)
        assertArrayEquals(day, CurrentReading.getRanges())
        assertEquals(CurrentReading.Plan("plan-a", 0), CurrentReading.getPlan())
    }
}
