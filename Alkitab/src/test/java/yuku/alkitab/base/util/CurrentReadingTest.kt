package yuku.alkitab.base.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class CurrentReadingTest {
    @Before
    fun setUp() {
        Preferences.invalidate()
        CurrentReading.clear()
    }

    @Test
    fun `get returns null when no current reading was set`() {
        assertNull(CurrentReading.get())
    }

    @Test
    fun `get returns the start and end aris that were set`() {
        CurrentReading.set(0x010203, 0x010210)

        assertArrayEquals(intArrayOf(0x010203, 0x010210), CurrentReading.get())
    }

    @Test
    fun `a later set replaces the earlier reading`() {
        CurrentReading.set(0x010203, 0x010210)
        CurrentReading.set(0x020101, 0x020105)

        assertArrayEquals(intArrayOf(0x020101, 0x020105), CurrentReading.get())
    }

    @Test
    fun `clear removes the current reading`() {
        CurrentReading.set(0x010203, 0x010210)
        CurrentReading.clear()

        assertNull(CurrentReading.get())
    }

    @Test
    fun `get defaults the end to 0 when only the start is stored`() {
        Preferences.setInt(Prefkey.current_reading_ari_start, 0x010203)

        assertArrayEquals(intArrayOf(0x010203, 0), CurrentReading.get())
    }

    @Test
    fun `a reading plan records the exact day and sequence selected`() {
        CurrentReading.setReadingPlan(0x270106, 0x270110, "plan-a", 12, 3)
        assertEquals(CurrentReading.Plan("plan-a", 12, 3), CurrentReading.getPlan())
        assertArrayEquals(intArrayOf(0x270106, 0x270110), CurrentReading.get())
    }

    @Test
    fun `opening a devotional list removes the reading plan completion target`() {
        CurrentReading.setReadingPlan(0x270106, 0x270110, "plan-a", 12, 3)
        val ranges = intArrayOf(0x280905, 0x280906, 0x28090e, 0x280917)
        CurrentReading.setRanges(ranges)
        assertArrayEquals(ranges, CurrentReading.get())
        assertNull(CurrentReading.getPlan())
        assertFalse(CurrentReading.completePlan())
    }

    @Test
    fun `dismissal removes both the guide ranges and the completion target`() {
        CurrentReading.setReadingPlan(0x270106, 0x270110, "plan-a", 12, 3)
        CurrentReading.clear()
        assertNull(CurrentReading.get())
        assertNull(CurrentReading.getPlan())
        assertTrue(!Preferences.contains(Prefkey.current_reading_ranges))
    }

    @Test
    fun `corrupt persisted range data does not crash the drawer`() {
        Preferences.setString(Prefkey.current_reading_ranges, "broken")
        assertNull(CurrentReading.get())
        Preferences.setString(Prefkey.current_reading_ranges, "[1,2,3]")
        assertNull(CurrentReading.get())
    }
}
