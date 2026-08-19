package yuku.alkitab.base.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.base.audio.model.AudioSet

/**
 * Pure-logic tests for [AudioBarController.canChooseSet], which gates the audio
 * bar's recording-chooser button.
 */
class AudioBarControllerSetChoiceTest {

    private fun set(audioId: String) = AudioSet(
        audioId = audioId,
        title = audioId.replaceFirstChar { it.uppercaseChar() },
        hasTiming = true,
        books_1 = (1..66).toSet(),
        mp3UrlTemplate = "/audio/file/p/$audioId/{book_1}/{chapter_1}.mp3",
        timingUrlTemplate = "/audio/timing/p/$audioId/{book_1}/{chapter_1}.json",
    )

    private fun canChooseSet(setsByVersion: Map<String, List<AudioSet>?>, versionIds: List<String>? = null) =
        AudioBarController.canChooseSet(
            versionIds = versionIds ?: setsByVersion.keys.toList(),
            setsOf = { setsByVersion[it] },
        )

    @Test
    fun `a single version offering one recording leaves nothing to choose`() {
        assertFalse(canChooseSet(mapOf("tb" to listOf(set("alkitabsuara")))))
    }

    @Test
    fun `a single version offering two recordings is a choice`() {
        assertTrue(canChooseSet(mapOf("tb" to listOf(set("alkitabsuara"), set("other")))))
    }

    @Test
    fun `in split view one recording on each side is still a choice, since audio can move across the splits`() {
        assertTrue(
            canChooseSet(
                mapOf(
                    "tb" to listOf(set("alkitabsuara")),
                    "kjv" to listOf(set("wordproject")),
                ),
            )
        )
    }

    @Test
    fun `in split view a single recording with the other side having no audio leaves nothing to choose`() {
        assertFalse(
            canChooseSet(
                mapOf(
                    "tb" to listOf(set("alkitabsuara")),
                    "kjv" to emptyList(),
                ),
            )
        )
    }

    @Test
    fun `versions whose set list has not resolved yet count as offering nothing`() {
        assertFalse(
            canChooseSet(
                mapOf(
                    "tb" to listOf(set("alkitabsuara")),
                    "kjv" to null,
                ),
            )
        )
    }

    @Test
    fun `the same version shown in both splits is counted once`() {
        assertFalse(
            canChooseSet(
                setsByVersion = mapOf("tb" to listOf(set("alkitabsuara"))),
                versionIds = listOf("tb", "tb"),
            )
        )
    }
}
