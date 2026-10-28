package yuku.alkitab.songs

import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SongVideoMetadataTest {
    @Test
    fun `view counts retain the requested precision at every boundary`() {
        val cases = mapOf(
            0L to "0", 999L to "999", 1_000L to "1.0K", 1_299L to "1.2K",
            9_999L to "9.9K", 10_000L to "10K", 344_999L to "344K", 999_999L to "999K",
            1_000_000L to "1.0M", 1_299_999L to "1.2M", 9_999_999L to "9.9M",
            10_000_000L to "10M", 10_999_999L to "10M", Long.MAX_VALUE to "9223372036854M",
        )
        for ((count, expected) in cases) assertEquals("$count", expected, formatSongVideoViewCount(count))
    }

    @Test
    fun `Indonesian metadata uses compact views and day month year`() {
        val published = Instant.parse("2025-09-21T12:00:00Z")
        val locale = Locale.forLanguageTag("id-ID")
        assertEquals("▷344K • 21 Sep 2025", formatSongVideoMetadata(344_000, false, published, locale))
        assertEquals("▷~344K • 21 Sep 2025", formatSongVideoMetadata(344_000, true, published, locale))
    }

    @Test
    fun `other app locales retain their own medium date formatting`() {
        val published = Instant.parse("2025-09-21T12:00:00Z")
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.JAPAN, Locale.forLanguageTag("ar"))) {
            val expected = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date.from(published))
            assertEquals(expected, formatSongVideoDate(published, locale))
        }
    }

    @Test
    fun `missing metadata does not leave a dangling separator or fabricate a view count`() {
        val published = Instant.parse("2025-09-21T12:00:00Z")
        val locale = Locale.forLanguageTag("id")
        assertEquals("▷~344K", formatSongVideoMetadata(344_000, true, null, locale))
        assertEquals("▷0", formatSongVideoMetadata(0, false, null, locale))
        assertEquals("21 Sep 2025", formatSongVideoMetadata(null, false, published, locale))
        assertEquals("", formatSongVideoMetadata(null, false, null, locale))
    }

    @Test
    fun `publication dates stay on their UTC calendar day across device time zones`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals("21 Sep 2025", formatSongVideoDate(Instant.parse("2025-09-21T00:30:00Z"), Locale.forLanguageTag("id")))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
