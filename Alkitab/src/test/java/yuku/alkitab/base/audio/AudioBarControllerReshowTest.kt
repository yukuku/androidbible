package yuku.alkitab.base.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the audio-bar auto-reshow decision used after the
 * activity is recreated (rotation) or returns from the background while the
 * [BibleAudioService] keeps playing. See [AudioBarController.reshowIfSessionActive].
 */
class AudioBarControllerReshowTest {

    private fun state(bookId: Int): PlaybackState =
        PlaybackState.IDLE.copy(bookId = bookId, chapter_1 = 1, versionId = "preset/in-tb")

    @Test
    fun `reshow binds when the service is mid-session and the bar is not already managed`() {
        assertTrue(AudioBarController.shouldBindForReshow(requestedVisible = false, hasActiveSession = true))
    }

    @Test
    fun `reshow does not bind when the service is idle`() {
        assertFalse(AudioBarController.shouldBindForReshow(requestedVisible = false, hasActiveSession = false))
    }

    @Test
    fun `reshow does not bind when the bar is already shown by this controller`() {
        assertFalse(AudioBarController.shouldBindForReshow(requestedVisible = true, hasActiveSession = true))
    }

    @Test
    fun `pending reshow fires once an active state arrives`() {
        assertTrue(AudioBarController.shouldReshowNow(reshowPending = true, state = state(bookId = 5)))
    }

    @Test
    fun `pending reshow stays hidden against an idle state to avoid show-then-hide flicker`() {
        assertFalse(AudioBarController.shouldReshowNow(reshowPending = true, state = state(bookId = -1)))
    }

    @Test
    fun `no reshow when none is pending even if the state is active`() {
        assertFalse(AudioBarController.shouldReshowNow(reshowPending = false, state = state(bookId = 5)))
    }

    @Test
    fun `PlaybackState reports active once a chapter is loaded and idle otherwise`() {
        assertFalse(PlaybackState.IDLE.isActive)
        assertTrue(state(bookId = 0).isActive)
        assertTrue(state(bookId = 41).isActive)
    }
}
