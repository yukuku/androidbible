package yuku.alkitab.base.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import yuku.alkitab.base.audio.ui.AudioBarCommand
import yuku.alkitab.base.audio.ui.AudioBarUiState
import yuku.alkitab.debug.R

/**
 * Pure-logic tests for the audio load log's UI-event labels: the button
 * presses and bar state transitions [AudioBarController] records alongside
 * the HTTP and player events.
 */
class AudioBarControllerLoggingTest {

    @Test
    fun `button presses are described for the log`() {
        assertEquals(
            AudioLogMessage(R.string.audio_log_ui_play_pause),
            AudioBarController.describeCommand(AudioBarCommand.PlayPause),
        )
        assertEquals(
            AudioLogMessage(R.string.audio_log_ui_retry),
            AudioBarController.describeCommand(AudioBarCommand.Retry),
        )
        assertEquals(
            AudioLogMessage(R.string.audio_log_ui_open_log),
            AudioBarController.describeCommand(AudioBarCommand.OpenLogSheet),
        )
    }

    @Test
    fun `commands carrying a value include it as a format argument`() {
        assertEquals(
            AudioLogMessage(R.string.audio_log_ui_seek, listOf(4_200L)),
            AudioBarController.describeCommand(AudioBarCommand.SeekCommit(4_200L)),
        )
        assertEquals(
            AudioLogMessage(R.string.audio_log_ui_pick_set, listOf("alkitabsuara", "preset/in-tb")),
            AudioBarController.describeCommand(AudioBarCommand.PickSet("preset/in-tb", "alkitabsuara")),
        )
    }

    @Test
    fun `seek drag is not logged because it fires continuously while dragging`() {
        assertNull(AudioBarController.describeCommand(AudioBarCommand.SeekDrag(1_000L)))
    }

    private val paused = AudioBarUiState.HIDDEN.copy(visible = true)
    private val preparing = paused.copy(preparing = true)
    private val playing = paused.copy(isPlaying = true)
    private val errored = paused.copy(error = "IO_NETWORK_CONNECTION_FAILED")

    private fun transition(from: Int, to: Int) = AudioLogMessage(
        R.string.audio_log_state_transition,
        listOf(AudioLogStateLabel(from), AudioLogStateLabel(to)),
    )

    @Test
    fun `a change between rendered states is logged`() {
        assertEquals(
            transition(R.string.audio_log_state_paused, R.string.audio_log_state_preparing),
            AudioBarController.describeStateTransition(paused, preparing),
        )
        assertEquals(
            transition(R.string.audio_log_state_preparing, R.string.audio_log_state_playing),
            AudioBarController.describeStateTransition(preparing, playing),
        )
        assertEquals(
            transition(R.string.audio_log_state_preparing, R.string.audio_log_state_error),
            AudioBarController.describeStateTransition(preparing, errored),
        )
    }

    @Test
    fun `an unchanged state logs nothing`() {
        assertNull(AudioBarController.describeStateTransition(playing, playing))
    }

    @Test
    fun `a position tick during playback does not count as a state change`() {
        val laterTick = playing.copy(positionMs = 5_000L, verse_1 = 3)
        assertNull(AudioBarController.describeStateTransition(playing, laterTick))
    }

    @Test
    fun `an error outranks preparing and playing so a failing load reads as error`() {
        val erroredWhilePreparing = errored.copy(preparing = true, isPlaying = true)
        assertEquals(
            transition(R.string.audio_log_state_playing, R.string.audio_log_state_error),
            AudioBarController.describeStateTransition(playing, erroredWhilePreparing),
        )
    }
}
