package yuku.alkitab.base.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
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
        Preferences.remove(Prefkey.current_reading_ari_start)
        Preferences.remove(Prefkey.current_reading_ari_end)
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
}
