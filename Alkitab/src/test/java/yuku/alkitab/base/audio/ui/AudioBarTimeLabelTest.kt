package yuku.alkitab.base.audio.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the audio bar's mm:ss label helpers. Pure string formatting —
 * plain JUnit, no Android framework involved.
 */
class AudioBarTimeLabelTest {

    @Test
    fun `durationLabelText is blank while no real duration is known`() {
        assertEquals("", durationLabelText(0L))
        assertEquals("", durationLabelText(-1L))
    }

    @Test
    fun `durationLabelText formats a real duration as mm-ss`() {
        assertEquals("0:01", durationLabelText(1_000L))
        assertEquals("3:15", durationLabelText(195_000L))
    }

    @Test
    fun `formatMmSs pads seconds to two digits and clamps negatives to zero`() {
        assertEquals("0:00", formatMmSs(0L))
        assertEquals("0:00", formatMmSs(-500L))
        assertEquals("0:05", formatMmSs(5_400L))
        assertEquals("1:05", formatMmSs(65_000L))
        assertEquals("12:34", formatMmSs(754_999L))
    }
}
