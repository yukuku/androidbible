package yuku.alkitab.base.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure-logic tests for how the audio load log accumulates: which events belong
 * to the chapter currently on screen, and the cap that bounds the list.
 */
class BibleAudioServiceLogTest {

    private fun entry(message: String) = AudioLogEntry(timestampMs = 0L, message = message)

    @Test
    fun `an event from the current load is appended`() {
        val logs = listOf(entry("Loading Genesis 1"))
        val result = BibleAudioService.appendLogEntry(
            logs = logs,
            entry = entry("Connecting"),
            eventGeneration = 3,
            currentGeneration = 3,
        )
        assertEquals(listOf("Loading Genesis 1", "Connecting"), result.map { it.message })
    }

    @Test
    fun `a superseded load's trailing cancellation events are dropped`() {
        // The reported bug: switching chapters cancels the outgoing chapter's
        // in-flight calls, and OkHttp reports that cancellation after the new
        // load has already reset the log.
        val logs = listOf(entry("Loading Psalms 104"))
        val result = BibleAudioService.appendLogEntry(
            logs = logs,
            entry = entry("Response failed: stream was reset: CANCEL"),
            eventGeneration = 2,
            currentGeneration = 3,
        )
        assertSame("the list should not be rebuilt when nothing is recorded", logs, result)
    }

    @Test
    fun `an event from a later generation is dropped too`() {
        // Defensive: generations only move forward, but the rule is equality,
        // not "newer wins", so a stale comparison can never leak either way.
        val logs = listOf(entry("Loading Genesis 1"))
        val result = BibleAudioService.appendLogEntry(
            logs = logs,
            entry = entry("Connecting"),
            eventGeneration = 9,
            currentGeneration = 3,
        )
        assertSame(logs, result)
    }

    @Test
    fun `the log keeps the newest entries once it reaches the cap`() {
        val logs = (1..4).map { entry("event $it") }
        val result = BibleAudioService.appendLogEntry(
            logs = logs,
            entry = entry("event 5"),
            eventGeneration = 1,
            currentGeneration = 1,
            maxEntries = 3,
        )
        assertEquals(listOf("event 3", "event 4", "event 5"), result.map { it.message })
    }

    @Test
    fun `a log exactly at the cap drops its oldest entry to make room`() {
        val logs = (1..3).map { entry("event $it") }
        val result = BibleAudioService.appendLogEntry(
            logs = logs,
            entry = entry("event 4"),
            eventGeneration = 1,
            currentGeneration = 1,
            maxEntries = 3,
        )
        assertEquals(listOf("event 2", "event 3", "event 4"), result.map { it.message })
    }
}
