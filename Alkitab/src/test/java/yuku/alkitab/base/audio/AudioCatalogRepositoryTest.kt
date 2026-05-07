package yuku.alkitab.base.audio

import android.app.Application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.debug.BuildConfig

/**
 * Robolectric tests for [AudioCatalogRepository] — Robolectric is required so
 * that `App.context.assets.open(...)` can read the bundled
 * `assets/audio_catalog.json` from the test classpath.
 *
 * The repository is a Kotlin `object` and therefore holds process-global state
 * across tests. We reset it via reflection in [tearDown].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AudioCatalogRepositoryTest {

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        resetCachedField()
        // Make sure no override file from a previous test leaks in.
        overrideFile(app).delete()
    }

    @After
    fun tearDown() {
        resetCachedField()
        overrideFile(RuntimeEnvironment.getApplication()).delete()
    }

    @Test
    fun `bundled fallback exposes the four SABDA versions when no override exists`() {
        val catalog = runBlocking { AudioCatalogRepository.loadCatalog() }
        val ids = catalog.entries.map { it.versionId }
        assertEquals(
            "the bundled assets/audio_catalog.json should ship with TB, AYT, AVB, KJV",
            listOf("preset/in-tb", "preset/in-ayt", "preset/in-avb", "preset/en-kjv"),
            ids,
        )
    }

    @Test
    fun `findEntry returns the entry whose versionId matches`() {
        runBlocking { AudioCatalogRepository.loadCatalog() }
        val tb = AudioCatalogRepository.findEntry("preset/in-tb")
        assertNotNull(tb)
        assertEquals("TB", tb!!.shortName)
        assertTrue(tb.chapterUrlTemplate.contains("{bookId}"))
        assertTrue(tb.chapterUrlTemplate.contains("{chapter_1}"))
    }

    @Test
    fun `findEntry returns null for an unknown versionId`() {
        runBlocking { AudioCatalogRepository.loadCatalog() }
        assertNull(AudioCatalogRepository.findEntry("preset/zz-nonsense"))
        assertNull(AudioCatalogRepository.findEntry(null))
    }

    @Test
    fun `isAudioAvailable is true for catalog versions and false for everything else`() {
        runBlocking { AudioCatalogRepository.loadCatalog() }
        assertTrue(AudioCatalogRepository.isAudioAvailable("preset/in-tb"))
        assertTrue(AudioCatalogRepository.isAudioAvailable("preset/en-kjv"))
        assertFalse(AudioCatalogRepository.isAudioAvailable("preset/zz-nonsense"))
        assertFalse(AudioCatalogRepository.isAudioAvailable(null))
    }

    @Test
    fun `override file is preferred over the bundled asset`() {
        val app = RuntimeEnvironment.getApplication()
        // A two-entry override that does NOT include any of the four SABDA presets,
        // so we can tell which source loadCatalog() drew from.
        overrideFile(app).writeText(
            """
            {
              "generatedAt": 1700000000,
              "entries": [
                {
                  "versionId": "preset/test-only",
                  "shortName": "TST",
                  "chapterUrlTemplate": "/audio/chapter?versionId=preset%2Ftest-only&bookId={bookId}&chapter_1={chapter_1}"
                }
              ]
            }
            """.trimIndent()
        )
        resetCachedField()  // force a re-read

        val catalog = runBlocking { AudioCatalogRepository.loadCatalog() }
        assertEquals(1, catalog.entries.size)
        assertEquals("preset/test-only", catalog.entries.single().versionId)
        assertFalse(
            "loading the override should have shadowed the bundled SABDA entries",
            catalog.entries.any { it.versionId == "preset/in-tb" },
        )
    }

    @Test
    fun `internal versionId is mapped via BuildConfig INTERNAL_VERSION_AUDIO_ID`() {
        // Sanity: this test runs under the `plain` flavor, which maps internal -> preset/in-tb.
        // Other production flavors override INTERNAL_VERSION_AUDIO_ID in their build.gradle.kts.
        assertEquals("preset/in-tb", BuildConfig.INTERNAL_VERSION_AUDIO_ID)

        runBlocking { AudioCatalogRepository.loadCatalog() }

        val entry = AudioCatalogRepository.findEntry("internal")
        assertNotNull(
            "the internal version should resolve to the flavor's mapped audio entry",
            entry,
        )
        assertEquals("preset/in-tb", entry!!.versionId)
        assertTrue(AudioCatalogRepository.isAudioAvailable("internal"))
    }

    @Test
    fun `findEntry passes preset versionIds through unchanged (no mapping for non-internal)`() {
        runBlocking { AudioCatalogRepository.loadCatalog() }
        // The mapping override only applies to "internal"; presets resolve directly.
        assertEquals(
            "preset/en-kjv",
            AudioCatalogRepository.findEntry("preset/en-kjv")?.versionId,
        )
    }

    @Test
    fun `concurrent first-callers of loadCatalog return the same instance and don't tear`() = runBlocking {
        // 32 coroutines fan out from the same starting line. With the loadMutex
        // in place, only one of them does the actual disk read; the rest take
        // the cached reference. Either way, every caller must observe the same
        // AudioCatalog object — *not* just a structurally-equal one — to prove
        // the cache write is correctly published.
        resetCachedField()
        val catalogs = withContext(Dispatchers.Default) {
            (1..32).map { async { AudioCatalogRepository.loadCatalog() } }.awaitAll()
        }
        val first = catalogs.first()
        for ((i, c) in catalogs.withIndex()) {
            assertTrue(
                "caller #$i got a different AudioCatalog instance than caller #0",
                c === first,
            )
        }
        assertEquals(4, first.entries.size)
    }

    @Test
    fun `unparseable override falls back to the bundled asset`() {
        val app = RuntimeEnvironment.getApplication()
        overrideFile(app).writeText("{ this is not json")
        resetCachedField()

        val catalog = runBlocking { AudioCatalogRepository.loadCatalog() }
        assertEquals(
            "should have fallen back to the four bundled entries",
            4,
            catalog.entries.size,
        )
        assertTrue(catalog.entries.any { it.versionId == "preset/in-tb" })
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun overrideFile(app: Application): File {
        return File(app.filesDir, "audio_catalog.json")
    }

    /**
     * The repository caches the parsed catalog in a `@Volatile` field. Reset it
     * between tests so the bundled-vs-override switch isn't confounded by carryover
     * state. Using reflection avoids exposing test-only API on the object itself.
     */
    private fun resetCachedField() {
        val field = AudioCatalogRepository.javaClass.getDeclaredField("cached")
        field.isAccessible = true
        field.set(AudioCatalogRepository, null)
    }
}
