package yuku.alkitab.base.audio.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.base.audio.AudioSetsRepositoryTest

/**
 * Pins the `/audio/sets` model contract: parse of the documented response
 * shape, the loud failure on missing required fields, and [AudioSet.coversBook]'s
 * 0-based → 1-based conversion at both ends of the Bible.
 */
class AudioSetTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun set(books_1: Set<Int>): AudioSet = AudioSet(
        audioId = "x",
        title = "X",
        hasTiming = true,
        books_1 = books_1,
        mp3UrlTemplate = "/audio/file/p/x/{book_1}/{chapter_1}.mp3",
        timingUrlTemplate = "/audio/timing/p/x/{book_1}/{chapter_1}.json",
    )

    @Test
    fun `the documented sets response parses, with books_1 becoming a Set at parse time`() {
        val parsed = json.decodeFromString(AudioSets.serializer(), AudioSetsRepositoryTest.IN_TB_SETS_JSON)
        assertEquals(2, parsed.schema)
        assertEquals("in-tb", parsed.preset)
        assertEquals(2, parsed.sets.size)

        val alkitabsuara = parsed.sets[0]
        assertEquals("alkitabsuara", alkitabsuara.audioId)
        assertTrue(alkitabsuara.hasTiming)
        assertEquals((1..66).toSet(), alkitabsuara.books_1)
        assertEquals("/audio/timing/in-tb/alkitabsuara/{book_1}/{chapter_1}.json", alkitabsuara.timingUrlTemplate)

        val davar = parsed.sets[1]
        assertFalse(davar.hasTiming)
        assertNull("timingUrlTemplate is explicitly null when hasTiming is false", davar.timingUrlTemplate)
    }

    @Test
    fun `coversBook converts the 0-based bookId at both ends of the Bible`() {
        val full = set((1..66).toSet())
        assertTrue("Genesis: bookId 0 must look up books_1 value 1", full.coversBook(0))
        assertTrue("Revelation: bookId 65 must look up books_1 value 66", full.coversBook(65))
        assertFalse("bookId 66 would be books_1 value 67, outside any Bible", full.coversBook(66))
    }

    @Test
    fun `coversBook reports the ragged davar coverage (books_1 11-14 absent)`() {
        val davar = set(((1..10) + (15..66)).toSet())
        assertTrue(davar.coversBook(9)) // books_1 10
        assertFalse(davar.coversBook(10)) // books_1 11
        assertFalse(davar.coversBook(13)) // books_1 14
        assertTrue(davar.coversBook(14)) // books_1 15
    }

    @Test
    fun `a missing required field fails the parse instead of defaulting`() {
        // No `sets` key at all: must not silently read as an empty list.
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(AudioSets.serializer(), """{"schema":2,"preset":"in-tb"}""")
        }
        // A set without books_1.
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                AudioSets.serializer(),
                """
                {"schema":2,"preset":"in-tb","sets":[
                  {"audioId":"a","title":"A","hasTiming":false,
                   "mp3UrlTemplate":"/m","timingUrlTemplate":null}
                ]}
                """.trimIndent()
            )
        }
        // A set omitting timingUrlTemplate; the contract requires an explicit null.
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                AudioSets.serializer(),
                """
                {"schema":2,"preset":"in-tb","sets":[
                  {"audioId":"a","title":"A","hasTiming":false,"books_1":[1],"mp3UrlTemplate":"/m"}
                ]}
                """.trimIndent()
            )
        }
    }
}
