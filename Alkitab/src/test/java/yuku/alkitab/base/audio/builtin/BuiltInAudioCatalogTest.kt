package yuku.alkitab.base.audio.builtin

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BuiltInAudioCatalogTest {

    private val catalog by lazy {
        BuiltInAudioCatalog(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `WEB exposes the public-domain David Williams recording`() {
        val set = catalog.audioSetForPreset("en-web")

        assertNotNull(set)
        assertEquals(BuiltInAudioCatalog.AUDIO_ID, set!!.audioId)
        assertEquals("WEB — David Williams (public domain)", set.title)
        assertEquals((1..66).toSet(), set.books_1)
        assertFalse(set.hasTiming)
        assertEquals(BuiltInAudioCatalog.LOCATOR, set.mp3UrlTemplate)
        assertNull(set.timingUrlTemplate)
    }

    @Test
    fun `non-WEB presets have no built-in recording`() {
        assertNull(catalog.audioSetForPreset("in-tb"))
        assertNull(catalog.audioSetForPreset("en-kjv"))
        assertNull(catalog.audioSetForPreset(""))
    }

    @Test
    fun `manifest covers every canonical chapter exactly once`() {
        val chapterCounts = intArrayOf(
            50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13, 10,
            42, 150, 31, 12, 8, 66, 52, 5, 48, 12, 14, 3, 9, 1, 4, 7, 3, 3, 3, 2,
            14, 4, 28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3, 1,
            13, 5, 5, 3, 5, 1, 1, 1, 22,
        )

        val urls = chapterCounts.flatMapIndexed { bookId, count ->
            (1..count).map { chapter -> catalog.chapterUrl(BuiltInAudioCatalog.AUDIO_ID, bookId, chapter) }
        }

        assertEquals(1_189, urls.size)
        assertTrue(urls.all { it != null })
        assertEquals(1_189, urls.filterNotNull().toSet().size)
        assertNull(catalog.chapterUrl(BuiltInAudioCatalog.AUDIO_ID, 0, 51))
        assertNull(catalog.chapterUrl(BuiltInAudioCatalog.AUDIO_ID, 66, 1))
        assertNull(catalog.chapterUrl("another-recording", 0, 1))
    }

    @Test
    fun `known source anomalies retain their verified URLs`() {
        assertEquals(
            "https://audiotreasure.com/content/WEBD_AT/25_Lam5.mp3",
            catalog.chapterUrl(BuiltInAudioCatalog.AUDIO_ID, 24, 5),
        )
        assertEquals(
            "https://audiotreasure.com/content/WEBD_AT/38_Zechariah_14.mp3",
            catalog.chapterUrl(BuiltInAudioCatalog.AUDIO_ID, 37, 14),
        )
    }
}
