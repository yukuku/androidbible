package yuku.alkitab.base.audio

import android.app.Application
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.audio.model.ChapterTiming
import yuku.alkitab.debug.BuildConfig

/**
 * Robolectric tests for [BibleAudioRepository]. We piggyback on the M1 test
 * pattern: Robolectric gives us a real `App.context` so the catalog repo can
 * stream the bundled `assets/audio_catalog.json`, and we override its cache
 * via reflection between tests.
 *
 * URL building and the catalog-miss path are exercised end-to-end. The HTTP
 * layer (`fetchTiming` against a live URL) is not — `BuildConfig.SERVER_HOST`
 * can't be rewritten at test time, so we don't try to reach it; we instead
 * test the catalog-miss / no-timing-template branches plus a pure JSON parse
 * that pins the v1 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BibleAudioRepositoryTest {

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        resetCatalogCache()
        overrideFile(app).delete()
    }

    @After
    fun tearDown() {
        resetCatalogCache()
        overrideFile(RuntimeEnvironment.getApplication()).delete()
    }

    @Test
    fun `buildChapterUrl substitutes bookId and chapter_1 against the catalog template`() = runBlocking {
        val url = BibleAudioRepository.buildChapterUrl(
            versionId = "preset/in-tb",
            bookId = 41,
            chapter_1 = 3,
        )
        assertNotNull(url)
        assertTrue(
            "URL should be rooted at SERVER_HOST: $url",
            url!!.startsWith(BuildConfig.SERVER_HOST),
        )
        assertTrue("URL should mention bookId=41: $url", url.contains("bookId=41"))
        assertTrue("URL should mention chapter_1=3: $url", url.contains("chapter_1=3"))
        assertTrue("versionId stays URL-encoded in the template: $url", url.contains("preset%2Fin-tb"))
        // Sanity: no leftover placeholders.
        assertEquals(-1, url.indexOf("{bookId}"))
        assertEquals(-1, url.indexOf("{chapter_1}"))
    }

    @Test
    fun `buildChapterUrl works for KJV (different catalog entry)`() = runBlocking {
        val url = BibleAudioRepository.buildChapterUrl("preset/en-kjv", 0, 1)
        assertNotNull(url)
        assertTrue(url!!.contains("preset%2Fen-kjv"))
        assertTrue(url.contains("bookId=0"))
        assertTrue(url.contains("chapter_1=1"))
    }

    @Test
    fun `buildChapterUrl returns null for a version not in the catalog`() = runBlocking {
        val url = BibleAudioRepository.buildChapterUrl("preset/zz-nonsense", 0, 1)
        assertNull(url)
    }

    @Test
    fun `buildChapterUrl resolves the internal version via BuildConfig INTERNAL_VERSION_AUDIO_ID`() = runBlocking {
        // The plain flavor maps internal -> preset/in-tb.
        assertEquals("preset/in-tb", BuildConfig.INTERNAL_VERSION_AUDIO_ID)
        val url = BibleAudioRepository.buildChapterUrl("internal", 41, 3)
        assertNotNull(url)
        assertTrue(url!!.contains("preset%2Fin-tb"))
    }

    @Test
    fun `fetchTiming returns null when the catalog has no entry for the version`() = runBlocking {
        val timing = BibleAudioRepository.fetchTiming("preset/zz-nonsense", 0, 1)
        assertNull(timing)
    }

    @Test
    fun `fetchTiming returns null when the catalog entry has no timingUrlTemplate`() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        // Stand up a single-entry catalog whose timingUrlTemplate is missing,
        // forcing the "no timing for this version" branch.
        overrideFile(app).writeText(
            """
            {
              "generatedAt": 1700000000,
              "entries": [
                {
                  "versionId": "preset/no-timing",
                  "shortName": "NTM",
                  "chapterUrlTemplate": "/audio/chapter?versionId=preset%2Fno-timing&bookId={bookId}&chapter_1={chapter_1}"
                }
              ]
            }
            """.trimIndent()
        )
        resetCatalogCache()

        val timing = BibleAudioRepository.fetchTiming("preset/no-timing", 0, 1)
        assertNull(timing)
    }

    @Test
    fun `ChapterTiming v1 schema parses the documented backend response shape`() {
        // Pin the JSON shape from docs/features/audio-bible/backend-plan.md §4.2.
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val raw = """
            {
              "schema": 1,
              "versionId": "preset/in-tb",
              "bookId": 41,
              "chapter_1": 3,
              "durationMs": 195000,
              "verses": [
                { "verse_1": 1, "startMs": 0,     "endMs": 5820  },
                { "verse_1": 2, "startMs": 5820,  "endMs": 11960 },
                { "verse_1": 3, "startMs": 11960, "endMs": 18100 }
              ],
              "generatedAt": 1713456000000
            }
        """.trimIndent()
        val parsed = json.decodeFromString(ChapterTiming.serializer(), raw)
        assertEquals("preset/in-tb", parsed.versionId)
        assertEquals(41, parsed.bookId)
        assertEquals(3, parsed.chapter_1)
        assertEquals(195_000L, parsed.durationMs)
        assertEquals(3, parsed.verses.size)
        assertEquals(2, parsed.verses[1].verse_1)
        assertEquals(5_820L, parsed.verses[1].startMs)
        assertEquals(11_960L, parsed.verses[1].endMs)
    }

    @Test
    fun `ChapterTiming parses the empty-verses payload (chapter exists but has no timing)`() {
        // Per backend-plan §4.2: a 200 response with verses=[] is the "version
        // known, chapter in range, but upstream has no timing" signal.
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val raw = """
            {
              "schema": 1,
              "versionId": "preset/in-tb",
              "bookId": 41,
              "chapter_1": 3,
              "durationMs": 0,
              "verses": [],
              "generatedAt": 0
            }
        """.trimIndent()
        val parsed = json.decodeFromString(ChapterTiming.serializer(), raw)
        assertTrue(parsed.verses.isEmpty())
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun overrideFile(app: Application): File = File(app.filesDir, "audio_catalog.json")

    /** Reset [AudioCatalogRepository.cached] between tests. */
    private fun resetCatalogCache() {
        val field = AudioCatalogRepository.javaClass.getDeclaredField("cached")
        field.isAccessible = true
        field.set(AudioCatalogRepository, null)
    }
}
