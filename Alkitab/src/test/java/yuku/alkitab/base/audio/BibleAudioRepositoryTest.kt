package yuku.alkitab.base.audio

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import yuku.alkitab.debug.BuildConfig

/**
 * Plain-JUnit tests for [BibleAudioRepository]. The set list comes from
 * [AudioSetsRepository] driven through its test seams, and the timing fetch
 * goes through [BibleAudioRepository]'s own [AudioHttp] seam — no live server,
 * no Android framework.
 */
class BibleAudioRepositoryTest {

    private class FakeHttp(private val bodyByUrl: (String) -> String?) : AudioHttp {
        val calls = AtomicInteger()
        val urls = mutableListOf<String>()
        override suspend fun getBody(url: String): String? {
            calls.incrementAndGet()
            urls.add(url)
            return bodyByUrl(url)
        }
    }

    private val defaultRepositoryHttp = BibleAudioRepository.http

    @Before
    fun setUp() {
        AudioSetsRepository.resetForTest()
        AudioSetsRepository.presetNameResolver = AudioSetsRepository.PresetNameResolver { versionId ->
            when (versionId) {
                "preset/in-tb", "internal" -> "in-tb"
                else -> null
            }
        }
        AudioSetsRepository.http = AudioHttp { url ->
            if (url.endsWith("/audio/sets/in-tb")) AudioSetsRepositoryTest.IN_TB_SETS_JSON else null
        }
    }

    @After
    fun tearDown() {
        AudioSetsRepository.resetForTest()
        BibleAudioRepository.http = defaultRepositoryHttp
    }

    // -- buildChapterUrl ----------------------------------------------------------

    @Test
    fun `buildChapterUrl expands the set's template with the one bookId to book_1 conversion`() = runBlocking {
        val url = BibleAudioRepository.buildChapterUrl("preset/in-tb", "alkitabsuara", bookId = 0, chapter_1 = 1)
        assertEquals(
            "Genesis is bookId 0 but book_1 1 in every audio URL",
            BuildConfig.SERVER_HOST + "/audio/file/in-tb/alkitabsuara/1/1.mp3",
            url,
        )

        val revelation = BibleAudioRepository.buildChapterUrl("preset/in-tb", "alkitabsuara", bookId = 65, chapter_1 = 22)
        assertEquals(BuildConfig.SERVER_HOST + "/audio/file/in-tb/alkitabsuara/66/22.mp3", revelation)
    }

    @Test
    fun `buildChapterUrl picks the requested recording, not the default`() = runBlocking {
        val url = BibleAudioRepository.buildChapterUrl("preset/in-tb", "davar", bookId = 41, chapter_1 = 3)
        assertNotNull(url)
        assertTrue("must expand davar's template: $url", url!!.contains("/audio/file/in-tb/davar/42/3.mp3"))
    }

    @Test
    fun `buildChapterUrl returns null for an unknown audioId`() = runBlocking {
        assertNull(BibleAudioRepository.buildChapterUrl("preset/in-tb", "nonexistent", bookId = 0, chapter_1 = 1))
    }

    @Test
    fun `buildChapterUrl returns null for a version without audio`() = runBlocking {
        assertNull(BibleAudioRepository.buildChapterUrl("preset/zz-nonsense", "alkitabsuara", bookId = 0, chapter_1 = 1))
    }

    @Test
    fun `buildChapterUrl resolves the internal version via its flavor preset`() = runBlocking {
        val url = BibleAudioRepository.buildChapterUrl("internal", "alkitabsuara", bookId = 41, chapter_1 = 3)
        assertNotNull(url)
        assertTrue(url!!.contains("/audio/file/in-tb/alkitabsuara/42/3.mp3"))
    }

    // -- fetchTiming --------------------------------------------------------------

    @Test
    fun `fetchTiming parses the documented timing response`() = runBlocking {
        val http = FakeHttp { url ->
            if (url == BuildConfig.SERVER_HOST + "/audio/timing/in-tb/alkitabsuara/42/3.json") {
                """
                {
                  "schema": 2,
                  "preset": "in-tb",
                  "audioId": "alkitabsuara",
                  "book_1": 42,
                  "chapter_1": 3,
                  "durationMs": 195000,
                  "verses": [
                    { "verse_1": 1, "startMs": 0,     "endMs": 5820  },
                    { "verse_1": 2, "startMs": 5820,  "endMs": 11960 },
                    { "verse_1": 3, "startMs": 11960, "endMs": 18100 }
                  ]
                }
                """.trimIndent()
            } else {
                null
            }
        }
        BibleAudioRepository.http = http

        val timing = BibleAudioRepository.fetchTiming("preset/in-tb", "alkitabsuara", bookId = 41, chapter_1 = 3)
        assertNotNull(timing)
        assertEquals("in-tb", timing!!.preset)
        assertEquals("alkitabsuara", timing.audioId)
        assertEquals(42, timing.book_1)
        assertEquals(3, timing.chapter_1)
        assertEquals(195_000L, timing.durationMs)
        assertEquals(3, timing.verses.size)
        assertEquals(2, timing.verses[1].verse_1)
        assertEquals(5_820L, timing.verses[1].startMs)
        assertEquals(11_960L, timing.verses[1].endMs)
    }

    @Test
    fun `fetchTiming parses the empty-verses payload (audio exists, timing does not)`() = runBlocking {
        BibleAudioRepository.http = FakeHttp {
            """{"schema":2,"preset":"in-tb","audioId":"alkitabsuara","book_1":1,"chapter_1":1,"durationMs":0,"verses":[]}"""
        }
        val timing = BibleAudioRepository.fetchTiming("preset/in-tb", "alkitabsuara", bookId = 0, chapter_1 = 1)
        assertNotNull(timing)
        assertTrue(timing!!.verses.isEmpty())
    }

    @Test
    fun `fetchTiming skips the request entirely for a recording without timing`() = runBlocking {
        val http = FakeHttp { "should never be requested" }
        BibleAudioRepository.http = http

        // davar has hasTiming=false and a null timingUrlTemplate.
        val timing = BibleAudioRepository.fetchTiming("preset/in-tb", "davar", bookId = 0, chapter_1 = 1)
        assertNull(timing)
        assertEquals(0, http.calls.get())
    }

    @Test
    fun `fetchTiming returns null for an unknown audioId`() = runBlocking {
        assertNull(BibleAudioRepository.fetchTiming("preset/in-tb", "nonexistent", bookId = 0, chapter_1 = 1))
    }

    @Test
    fun `fetchTiming returns null on a transport failure`() = runBlocking {
        BibleAudioRepository.http = FakeHttp { null }
        assertNull(BibleAudioRepository.fetchTiming("preset/in-tb", "alkitabsuara", bookId = 0, chapter_1 = 1))
    }

    @Test
    fun `fetchTiming returns null on an unparseable body`() = runBlocking {
        BibleAudioRepository.http = FakeHttp { "{ not json" }
        assertNull(BibleAudioRepository.fetchTiming("preset/in-tb", "alkitabsuara", bookId = 0, chapter_1 = 1))
    }

    @Test
    fun `fetchTiming refetches past the HTTP cache when the first body is corrupt`() = runBlocking {
        // getBody plays the disk cache serving corrupted bytes; the
        // revalidating fetch plays the network serving the real payload.
        BibleAudioRepository.http = object : AudioHttp {
            override suspend fun getBody(url: String): String? = "{ corrupt bytes from the disk cache"
            override suspend fun getBodyRevalidating(url: String): String? =
                """{"schema":2,"preset":"in-tb","audioId":"alkitabsuara","book_1":1,"chapter_1":1,"durationMs":1000,"verses":[{"verse_1":1,"startMs":0,"endMs":1000}]}"""
        }
        val timing = BibleAudioRepository.fetchTiming("preset/in-tb", "alkitabsuara", bookId = 0, chapter_1 = 1)
        assertNotNull(timing)
        assertEquals(1, timing!!.verses.size)
        assertEquals(1_000L, timing.durationMs)
    }

    @Test
    fun `fetchTiming returns null when a required timing field is missing`() = runBlocking {
        // durationMs absent — must fail the parse, not default to zero.
        BibleAudioRepository.http = FakeHttp {
            """{"schema":2,"preset":"in-tb","audioId":"alkitabsuara","book_1":1,"chapter_1":1,"verses":[]}"""
        }
        assertNull(BibleAudioRepository.fetchTiming("preset/in-tb", "alkitabsuara", bookId = 0, chapter_1 = 1))
    }
}
