package yuku.alkitab.base.audio

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.model.Version

/**
 * Plain-JUnit tests for [AudioSetsRepository]. The HTTP boundary ([AudioHttp])
 * and the version→preset resolution ([AudioSetsRepository.PresetNameResolver])
 * are seams, so no live server and no Android framework are needed.
 */
class AudioSetsRepositoryTest {

    /** Counting fake transport; the body per URL comes from [bodyByUrl]. */
    private class FakeHttp(
        private val delayMs: Long = 0,
        private val bodyByUrl: (String) -> String?,
    ) : AudioHttp {
        val calls = AtomicInteger()
        val urls = mutableListOf<String>()
        override suspend fun getBody(url: String): String? {
            calls.incrementAndGet()
            synchronized(urls) { urls.add(url) }
            if (delayMs > 0) delay(delayMs)
            return bodyByUrl(url)
        }
    }

    /**
     * Fake [VersionManager] with a fixed versionId→MVersion table, mirroring
     * the production contract that `getVersionFromVersionId` returns null for
     * the internal version and for unknown ids.
     */
    private class FakeVersionManager(
        private val byId: Map<String, MVersion>,
    ) : VersionManager {
        override fun activeVersion(): Version = throw UnsupportedOperationException()
        override fun activeMVersion(): MVersion = throw UnsupportedOperationException()
        override fun activeVersionId(): String = throw UnsupportedOperationException()
        override fun setActiveVersion(mv: MVersion) = throw UnsupportedOperationException()
        override fun getVersionFromVersionId(versionId: String?): MVersion? =
            if (versionId == MVersionInternal.getVersionInternalId()) null else byId[versionId]
        override fun getAvailableVersions(): List<MVersion> = throw UnsupportedOperationException()
        override fun getMVersionInternal(): MVersionInternal = MVersionInternal()
    }

    private fun dbVersion(presetName: String?, filename: String = "/x/y.yes"): MVersionDb =
        MVersionDb().apply {
            preset_name = presetName
            this.filename = filename
        }

    private val versionManager = FakeVersionManager(
        mapOf(
            "preset/in-tb" to dbVersion("in-tb"),
            "file//sdcard/some.yes" to dbVersion(null, "/sdcard/some.yes"),
        )
    )

    private fun installResolver() {
        AudioSetsRepository.presetNameResolver = AudioSetsRepository.PresetNameResolver { versionId ->
            AudioSetsRepository.presetNameFor(versionId, versionManager)
        }
    }

    @Before
    fun setUp() {
        AudioSetsRepository.resetForTest()
        installResolver()
    }

    @After
    fun tearDown() {
        AudioSetsRepository.resetForTest()
    }

    // -- version → preset mapping ------------------------------------------------

    @Test
    fun `a preset version maps to its preset_name`() {
        assertEquals("in-tb", AudioSetsRepository.presetNameFor("preset/in-tb", versionManager))
    }

    @Test
    fun `the internal version maps through MVersionInternal to the flavor's INTERNAL_VERSION_PRESET_NAME`() {
        // The plain flavor these tests run under declares "in-tb".
        assertEquals("in-tb", BuildConfig.INTERNAL_VERSION_PRESET_NAME)
        assertEquals("in-tb", AudioSetsRepository.presetNameFor("internal", versionManager))
    }

    @Test
    fun `a file version has no preset name`() {
        assertNull(AudioSetsRepository.presetNameFor("file//sdcard/some.yes", versionManager))
    }

    @Test
    fun `an unknown versionId has no preset name`() {
        assertNull(AudioSetsRepository.presetNameFor("preset/zz-nonsense", versionManager))
    }

    @Test
    fun `an empty preset name reads as no preset identity (the empty flavor default)`() {
        val emptyPreset = object : MVersion() {
            override fun getVersionId() = "preset/none"
            override fun getPresetName() = ""
            override fun getVersion(): Version? = null
            override fun getActive() = false
            override fun hasDataFile() = false
        }
        val vm = FakeVersionManager(mapOf("preset/none" to emptyPreset))
        assertNull(AudioSetsRepository.presetNameFor("preset/none", vm))
    }

    // -- setsFor ------------------------------------------------------------------

    @Test
    fun `setsFor fetches, parses, and caches the set list for a preset version`() = runBlocking {
        val http = FakeHttp { url -> if (url.endsWith("/audio/sets/in-tb")) IN_TB_SETS_JSON else null }
        AudioSetsRepository.http = http

        val sets = AudioSetsRepository.setsFor("preset/in-tb")
        assertEquals(2, sets.schema)
        assertEquals("in-tb", sets.preset)
        assertEquals(listOf("alkitabsuara", "davar"), sets.sets.map { it.audioId })
        assertEquals(1, http.calls.get())
        assertTrue(http.urls.single().startsWith(BuildConfig.SERVER_HOST))

        val again = AudioSetsRepository.setsFor("preset/in-tb")
        assertSame("second call must come from the in-memory cache", sets, again)
        assertEquals(1, http.calls.get())
    }

    @Test
    fun `setsFor short-circuits a version without a preset name and issues no request`() = runBlocking {
        val http = FakeHttp { IN_TB_SETS_JSON }
        AudioSetsRepository.http = http

        val sets = AudioSetsRepository.setsFor("file//sdcard/some.yes")
        assertTrue(sets.sets.isEmpty())
        assertEquals(0, http.calls.get())
    }

    @Test
    fun `the internal version resolves against its flavor preset`() = runBlocking {
        val http = FakeHttp { url -> if (url.endsWith("/audio/sets/in-tb")) IN_TB_SETS_JSON else null }
        AudioSetsRepository.http = http

        val sets = AudioSetsRepository.setsFor("internal")
        assertEquals("in-tb", sets.preset)
        assertEquals(2, sets.sets.size)
    }

    @Test
    fun `an empty sets array is a normal answer and is cached`() = runBlocking {
        val http = FakeHttp { """{"schema":2,"preset":"in-tb","sets":[]}""" }
        AudioSetsRepository.http = http

        val sets = AudioSetsRepository.setsFor("preset/in-tb")
        assertTrue(sets.sets.isEmpty())
        assertEquals(2, sets.schema)

        AudioSetsRepository.setsFor("preset/in-tb")
        assertEquals("the negative answer must be served from cache", 1, http.calls.get())
    }

    @Test
    fun `a transport failure resolves to an empty set list and is cached only for the cooldown`() = runBlocking {
        val http = FakeHttp { null }
        AudioSetsRepository.http = http
        var now = 0L
        AudioSetsRepository.nanoTime = { now }

        val sets = AudioSetsRepository.setsFor("preset/in-tb")
        assertTrue(sets.sets.isEmpty())

        now += TimeUnit.SECONDS.toNanos(29)
        AudioSetsRepository.setsFor("preset/in-tb")
        assertEquals("no retry loop: within the cooldown the failure is cached", 1, http.calls.get())

        now += TimeUnit.SECONDS.toNanos(2)
        AudioSetsRepository.setsFor("preset/in-tb")
        assertEquals("past the cooldown the failure is re-queried", 2, http.calls.get())
    }

    @Test
    fun `an expired failure reads as unresolved so menu preparation kicks off a fresh query`() = runBlocking {
        AudioSetsRepository.http = FakeHttp { null }
        var now = 0L
        AudioSetsRepository.nanoTime = { now }

        AudioSetsRepository.setsFor("preset/in-tb")
        assertNotNull("within the cooldown the failed answer is served", AudioSetsRepository.cachedSetsFor("preset/in-tb"))

        now += TimeUnit.SECONDS.toNanos(31)
        assertNull(
            "an expired failure must read as unresolved, otherwise the audio icon stays hidden forever",
            AudioSetsRepository.cachedSetsFor("preset/in-tb"),
        )
    }

    @Test
    fun `a recovered fetch after a failed one replaces the cached answer for good`() = runBlocking {
        var body: String? = null
        val http = FakeHttp { body }
        AudioSetsRepository.http = http
        var now = 0L
        AudioSetsRepository.nanoTime = { now }

        assertTrue(AudioSetsRepository.setsFor("preset/in-tb").sets.isEmpty())

        body = IN_TB_SETS_JSON
        now += TimeUnit.SECONDS.toNanos(31)
        assertEquals(2, AudioSetsRepository.setsFor("preset/in-tb").sets.size)

        now += TimeUnit.DAYS.toNanos(1)
        assertEquals("a resolved answer never expires", 2, AudioSetsRepository.setsFor("preset/in-tb").sets.size)
        assertEquals(2, http.calls.get())
    }

    @Test
    fun `a version with no preset identity is a resolved answer and never expires`() = runBlocking {
        val http = FakeHttp { IN_TB_SETS_JSON }
        AudioSetsRepository.http = http
        var now = 0L
        AudioSetsRepository.nanoTime = { now }

        assertTrue(AudioSetsRepository.setsFor("file//sdcard/some.yes").sets.isEmpty())
        now += TimeUnit.DAYS.toNanos(1)
        assertNotNull(AudioSetsRepository.cachedSetsFor("file//sdcard/some.yes"))
        assertEquals(0, http.calls.get())
    }

    @Test
    fun `an empty answer from the server never expires`() = runBlocking {
        val http = FakeHttp { """{"schema":2,"preset":"in-tb","sets":[]}""" }
        AudioSetsRepository.http = http
        var now = 0L
        AudioSetsRepository.nanoTime = { now }

        assertTrue(AudioSetsRepository.setsFor("preset/in-tb").sets.isEmpty())
        now += TimeUnit.DAYS.toNanos(1)
        AudioSetsRepository.setsFor("preset/in-tb")
        assertEquals("'no recordings' is an answer, not a failure", 1, http.calls.get())
    }

    @Test
    fun `an unparseable body resolves to an empty set list after both fetch attempts`() = runBlocking {
        val http = FakeHttp { "{ this is not json" }
        AudioSetsRepository.http = http
        val sets = AudioSetsRepository.setsFor("preset/in-tb")
        assertTrue(sets.sets.isEmpty())
        assertEquals(
            "a parse failure triggers one revalidating refetch (the fake's default delegates to getBody)",
            2,
            http.calls.get(),
        )
    }

    @Test
    fun `a corrupt cached body is refetched past the HTTP cache and the fresh payload is used`() = runBlocking {
        // getBody plays the disk cache serving corrupted bytes; the
        // revalidating fetch plays the network serving the real payload.
        val http = object : AudioHttp {
            var plainCalls = 0
            var revalidatingCalls = 0
            override suspend fun getBody(url: String): String? {
                plainCalls++
                return "{ corrupt bytes from the disk cache"
            }
            override suspend fun getBodyRevalidating(url: String): String? {
                revalidatingCalls++
                return IN_TB_SETS_JSON
            }
        }
        AudioSetsRepository.http = http

        val sets = AudioSetsRepository.setsFor("preset/in-tb")
        assertEquals(listOf("alkitabsuara", "davar"), sets.sets.map { it.audioId })
        assertEquals(1, http.plainCalls)
        assertEquals(1, http.revalidatingCalls)
    }

    @Test
    fun `a set missing a required field fails the parse loudly instead of defaulting`() = runBlocking {
        // hasTiming is absent: the models declare no default parameter values,
        // so this contract violation must not produce a half-filled model.
        AudioSetsRepository.http = FakeHttp {
            """
            {"schema":2,"preset":"in-tb","sets":[
              {"audioId":"a","title":"A","books_1":[1],
               "mp3UrlTemplate":"/audio/file/in-tb/a/{book_1}/{chapter_1}.mp3","timingUrlTemplate":null}
            ]}
            """.trimIndent()
        }
        val sets = AudioSetsRepository.setsFor("preset/in-tb")
        assertTrue(sets.sets.isEmpty())
    }

    @Test
    fun `cachedSetsFor is null before resolution and the resolved instance afterwards`() = runBlocking {
        AudioSetsRepository.http = FakeHttp { IN_TB_SETS_JSON }
        assertNull(AudioSetsRepository.cachedSetsFor("preset/in-tb"))
        val resolved = AudioSetsRepository.setsFor("preset/in-tb")
        assertSame(resolved, AudioSetsRepository.cachedSetsFor("preset/in-tb"))
    }

    @Test
    fun `concurrent setsFor callers for one version issue a single request`() = runBlocking {
        val http = FakeHttp(delayMs = 100) { IN_TB_SETS_JSON }
        AudioSetsRepository.http = http

        val results = withContext(Dispatchers.Default) {
            (1..16).map { async { AudioSetsRepository.setsFor("preset/in-tb") } }.awaitAll()
        }
        assertEquals(1, http.calls.get())
        val first = results.first()
        assertNotNull(first)
        results.forEach { assertSame(first, it) }
    }

    companion object {
        /**
         * Two-recording answer shaped like the pinned backend contract:
         * `davar` has no timing and does not cover books 11–14.
         */
        val IN_TB_SETS_JSON = """
            {
              "schema": 2,
              "preset": "in-tb",
              "generatedAt": 1754357000000,
              "sets": [
                {
                  "audioId": "alkitabsuara",
                  "title": "Alkitab Suara",
                  "hasTiming": true,
                  "books_1": [${(1..66).joinToString(",")}],
                  "mp3UrlTemplate": "/audio/file/in-tb/alkitabsuara/{book_1}/{chapter_1}.mp3",
                  "timingUrlTemplate": "/audio/timing/in-tb/alkitabsuara/{book_1}/{chapter_1}.json"
                },
                {
                  "audioId": "davar",
                  "title": "davar",
                  "hasTiming": false,
                  "books_1": [${((1..10) + (15..66)).joinToString(",")}],
                  "mp3UrlTemplate": "/audio/file/in-tb/davar/{book_1}/{chapter_1}.mp3",
                  "timingUrlTemplate": null
                }
              ]
            }
        """.trimIndent()
    }
}
