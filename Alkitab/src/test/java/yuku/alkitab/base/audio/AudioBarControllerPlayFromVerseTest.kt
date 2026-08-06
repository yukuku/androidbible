package yuku.alkitab.base.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.base.audio.model.AudioSet

/**
 * Pure-logic tests for the "play audio from this verse" pieces of
 * [AudioBarController]: the timing-filtered recording-picker groups
 * ([AudioBarController.buildSetGroups]), the availability gate for the verse
 * action ([AudioBarController.hasPlayableOption]), and the start verse chosen
 * when switching recordings ([AudioBarController.startVerseForSetSwitch]).
 */
class AudioBarControllerPlayFromVerseTest {

    private fun set(audioId: String, hasTiming: Boolean, books_1: Set<Int> = (1..66).toSet()) = AudioSet(
        audioId = audioId,
        title = audioId.replaceFirstChar { it.uppercaseChar() },
        hasTiming = hasTiming,
        books_1 = books_1,
        mp3UrlTemplate = "/audio/file/p/$audioId/{book_1}/{chapter_1}.mp3",
        timingUrlTemplate = if (hasTiming) "/audio/timing/p/$audioId/{book_1}/{chapter_1}.json" else null,
    )

    private fun groups(
        setsByVersion: Map<String, List<AudioSet>?>,
        currentBookId: Int = 0,
        selectedVersionId: String? = null,
        selectedAudioId: String? = null,
        timedOnly: Boolean = true,
        versionsWithBook: Set<String> = setsByVersion.keys,
    ) = AudioBarController.buildSetGroups(
        versionIds = setsByVersion.keys.toList(),
        currentBookId = currentBookId,
        selectedVersionId = selectedVersionId,
        selectedAudioId = selectedAudioId,
        timedOnly = timedOnly,
        setsOf = { setsByVersion[it] },
        versionNameOf = { it.uppercase() },
        versionHasBook = { it in versionsWithBook },
    )

    @Test
    fun `timedOnly drops recordings without timing and versions left with none`() {
        val result = groups(
            mapOf(
                "tb" to listOf(set("timed", hasTiming = true), set("untimed", hasTiming = false)),
                "kjv" to listOf(set("plain", hasTiming = false)),
            ),
        )
        assertEquals(listOf("tb"), result.map { it.versionId })
        assertEquals(listOf("timed"), result.single().options.map { it.audioId })
    }

    @Test
    fun `without timedOnly untimed recordings stay listed, matching the audio bar's sheet`() {
        val result = groups(
            mapOf("tb" to listOf(set("timed", hasTiming = true), set("untimed", hasTiming = false))),
            timedOnly = false,
        )
        assertEquals(listOf("timed", "untimed"), result.single().options.map { it.audioId })
    }

    @Test
    fun `one group per version in visible order, unresolved and empty versions dropped`() {
        val result = groups(
            linkedMapOf(
                "tb" to listOf(set("a", hasTiming = true)),
                "unresolved" to null,
                "empty" to emptyList(),
                "kjv" to listOf(set("b", hasTiming = true)),
            ),
        )
        assertEquals(listOf("tb", "kjv"), result.map { it.versionId })
    }

    @Test
    fun `the session's recording is marked selected, others not`() {
        val result = groups(
            mapOf("tb" to listOf(set("a", hasTiming = true), set("b", hasTiming = true))),
            selectedVersionId = "tb",
            selectedAudioId = "b",
        )
        assertEquals(
            listOf(false, true),
            result.single().options.map { it.selected },
        )
    }

    @Test
    fun `coverage of the current book gates each row and the verse action`() {
        // Book id 39 (Matthew): covered by the NT-only set, not by the OT-only set.
        val result = groups(
            mapOf(
                "tb" to listOf(
                    set("nt", hasTiming = true, books_1 = (40..66).toSet()),
                    set("ot", hasTiming = true, books_1 = (1..39).toSet()),
                ),
            ),
            currentBookId = 39,
        )
        assertEquals(
            listOf(true, false),
            result.single().options.map { it.coversCurrentBook },
        )
        assertTrue(AudioBarController.hasPlayableOption(result))
    }

    @Test
    fun `a version that does not include the current book yields no playable option`() {
        val result = groups(
            mapOf("tb" to listOf(set("a", hasTiming = true))),
            versionsWithBook = emptySet(),
        )
        assertFalse(AudioBarController.hasPlayableOption(result))
    }

    @Test
    fun `no timed recording anywhere means the verse action is unavailable`() {
        val result = groups(
            mapOf(
                "tb" to listOf(set("untimed", hasTiming = false)),
                "kjv" to listOf(set("plain", hasTiming = false)),
            ),
        )
        assertFalse(AudioBarController.hasPlayableOption(result))
    }

    @Test
    fun `set switch start verse - a pending play-from-verse target wins over the playing verse`() {
        assertEquals(10, AudioBarController.startVerseForSetSwitch(newSetHasTiming = true, pendingStartVerse1 = 10, playingVerse1 = 3))
    }

    @Test
    fun `set switch start verse - without a pending target the playing verse carries over`() {
        assertEquals(3, AudioBarController.startVerseForSetSwitch(newSetHasTiming = true, pendingStartVerse1 = 0, playingVerse1 = 3))
    }

    @Test
    fun `set switch start verse - a recording without timing always starts at the chapter top`() {
        assertEquals(0, AudioBarController.startVerseForSetSwitch(newSetHasTiming = false, pendingStartVerse1 = 10, playingVerse1 = 3))
    }

    @Test
    fun `set switch start verse - nothing pending and nothing playing starts at the chapter top`() {
        assertEquals(0, AudioBarController.startVerseForSetSwitch(newSetHasTiming = true, pendingStartVerse1 = 0, playingVerse1 = 0))
    }
}
