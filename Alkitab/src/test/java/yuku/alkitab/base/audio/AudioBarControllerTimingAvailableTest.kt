package yuku.alkitab.base.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for [AudioBarController.computeTimingAvailable]: whether
 * verse highlight and verse-skip are enabled, given what the selected
 * recording declares up front and what playback has observed so far.
 */
class AudioBarControllerTimingAvailableTest {

    @Test
    fun `a recording known to have no timing disables the controls from the start`() {
        assertFalse(AudioBarController.computeTimingAvailable(setHasTiming = false, stateVerse1 = 0, currentTimingAvailable = false))
    }

    @Test
    fun `a recording without timing stays disabled even against a stray verse hit or a stale latch`() {
        assertFalse(AudioBarController.computeTimingAvailable(setHasTiming = false, stateVerse1 = 7, currentTimingAvailable = true))
    }

    @Test
    fun `a recording with timing enables once the first verse hit arrives and latches on`() {
        assertFalse(AudioBarController.computeTimingAvailable(setHasTiming = true, stateVerse1 = 0, currentTimingAvailable = false))
        assertTrue(AudioBarController.computeTimingAvailable(setHasTiming = true, stateVerse1 = 5, currentTimingAvailable = false))
        assertTrue(
            "between verses (verse 0) the earlier hit keeps the controls enabled",
            AudioBarController.computeTimingAvailable(setHasTiming = true, stateVerse1 = 0, currentTimingAvailable = true),
        )
    }

    @Test
    fun `an unknown recording (cold cache) falls back to the observed-verse inference`() {
        assertFalse(AudioBarController.computeTimingAvailable(setHasTiming = null, stateVerse1 = 0, currentTimingAvailable = false))
        assertTrue(AudioBarController.computeTimingAvailable(setHasTiming = null, stateVerse1 = 3, currentTimingAvailable = false))
    }
}
