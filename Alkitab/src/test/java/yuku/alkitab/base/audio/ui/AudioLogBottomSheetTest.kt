package yuku.alkitab.base.audio.ui

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the load-log timestamp formatter behind the audio bar's log
 * bottom sheet. Pure string formatting — plain JUnit, no Android framework
 * involved. Builds the expected instant via [ZonedDateTime] in the same
 * [ZoneId.systemDefault] the formatter itself uses, so the assertion holds
 * regardless of the JVM's actual default zone.
 */
class AudioLogBottomSheetTest {

    @Test
    fun `formatLogTimestamp renders HH-mm-ss-SSS zero-padded in the system default zone`() {
        val timestampMs = ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 6_000_000, ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals("03:04:05.006", formatLogTimestamp(timestampMs))
    }

    @Test
    fun `formatLogTimestamp pads single-digit hours minutes seconds and millis`() {
        val timestampMs = ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 1_000_000, ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals("00:00:00.001", formatLogTimestamp(timestampMs))
    }
}
