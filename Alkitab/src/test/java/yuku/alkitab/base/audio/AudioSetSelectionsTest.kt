package yuku.alkitab.base.audio

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import yuku.alkitab.base.audio.model.AudioSet

/**
 * Plain-JUnit tests for [AudioSetSelections]. The Preferences read/write is a
 * seam ([AudioSetSelections.storedJsonReader] / `storedJsonWriter`), replaced
 * here with an in-memory string, so the selection and fallback rules are
 * exercised without the Android SharedPreferences machinery.
 */
class AudioSetSelectionsTest {

    private var stored: String? = null
    private val defaultReader = AudioSetSelections.storedJsonReader
    private val defaultWriter = AudioSetSelections.storedJsonWriter

    private fun set(audioId: String): AudioSet = AudioSet(
        audioId = audioId,
        title = audioId.replaceFirstChar { it.uppercaseChar() },
        hasTiming = true,
        books_1 = (1..66).toSet(),
        mp3UrlTemplate = "/audio/file/p/$audioId/{book_1}/{chapter_1}.mp3",
        timingUrlTemplate = null,
    )

    private val sets = listOf(set("alkitabsuara"), set("davar"), set("hosanna"))

    @Before
    fun setUp() {
        stored = null
        AudioSetSelections.storedJsonReader = { stored }
        AudioSetSelections.storedJsonWriter = { stored = it }
    }

    @After
    fun tearDown() {
        AudioSetSelections.storedJsonReader = defaultReader
        AudioSetSelections.storedJsonWriter = defaultWriter
    }

    @Test
    fun `the default selection is the first set, in backend order`() {
        assertEquals("alkitabsuara", AudioSetSelections.resolve("preset/in-tb", sets)?.audioId)
        assertNull("nothing stored — the default must not be written back", stored)
    }

    @Test
    fun `a remembered audioId is honored`() {
        AudioSetSelections.store("preset/in-tb", "davar")
        assertEquals("davar", AudioSetSelections.resolve("preset/in-tb", sets)?.audioId)
    }

    @Test
    fun `a vanished audioId falls back to the first set and rewrites the stored map`() {
        AudioSetSelections.store("preset/in-tb", "gone-recording")
        assertEquals("alkitabsuara", AudioSetSelections.resolve("preset/in-tb", sets)?.audioId)
        assertEquals(
            "the stale entry must be overwritten so it never resurfaces",
            mapOf("preset/in-tb" to "alkitabsuara"),
            AudioSetSelections.parseMap(stored),
        )
    }

    @Test
    fun `an empty set list resolves to null`() {
        assertNull(AudioSetSelections.resolve("preset/in-tb", emptyList()))
    }

    @Test
    fun `the map holds one entry per version and round-trips through storage`() {
        AudioSetSelections.store("preset/in-tb", "davar")
        AudioSetSelections.store("preset/en-kjv", "wordproject")
        AudioSetSelections.store("preset/in-tb", "hosanna")

        assertEquals(
            mapOf("preset/in-tb" to "hosanna", "preset/en-kjv" to "wordproject"),
            AudioSetSelections.parseMap(stored),
        )
        assertEquals("hosanna", AudioSetSelections.resolve("preset/in-tb", sets)?.audioId)
    }

    @Test
    fun `an unparseable stored map reads as empty and the default applies`() {
        stored = "{ not json"
        assertTrue(AudioSetSelections.parseMap(stored).isEmpty())
        assertEquals("alkitabsuara", AudioSetSelections.resolve("preset/in-tb", sets)?.audioId)
    }

    @Test
    fun `choose picks the remembered set when present and the first otherwise`() {
        assertEquals("davar", AudioSetSelections.choose(sets, "davar")?.audioId)
        assertEquals("alkitabsuara", AudioSetSelections.choose(sets, null)?.audioId)
        assertEquals("alkitabsuara", AudioSetSelections.choose(sets, "gone")?.audioId)
        assertNull(AudioSetSelections.choose(emptyList(), "davar"))
    }
}
