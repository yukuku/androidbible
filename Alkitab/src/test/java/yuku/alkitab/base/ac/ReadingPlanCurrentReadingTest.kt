package yuku.alkitab.base.ac

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
    fun `opening a later passage activates the entire selected day and navigates to that passage`() {
        val day = intArrayOf(0x000100, 0x000300, 0x120101, 0x120106, 0x270101, 0x27010a)
        val activity = Robolectric.buildActivity(ReadingPlanActivity::class.java).get()
        activity.readingPlan = ReadingPlan().apply {
            info.name = "plan-a"
            dailyVerses = arrayOf(intArrayOf(0x020100, 0x020200), day)
        }
        activity.goToIsiActivity(1, 2)
        assertArrayEquals(day, CurrentReading.get())
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
        assertArrayEquals(day, CurrentReading.get())
        assertEquals(CurrentReading.Plan("plan-a", 0), CurrentReading.getPlan())
    }
}
