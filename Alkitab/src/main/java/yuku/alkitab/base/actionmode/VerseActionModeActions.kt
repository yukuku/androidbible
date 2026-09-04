package yuku.alkitab.base.actionmode

import yuku.alkitab.base.audio.RecordedAudioAvailability
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.util.IntArrayList

/**
 * Write-side surface that [VerseActionModeController] invokes to trigger Activity
 * operations in response to action-mode menu clicks. Implemented by the hosting
 * Activity.
 */
interface VerseActionModeActions {
    /** Clears the checked-verse state on the primary (split-0) list. */
    fun uncheckAllVersesSplit0()

    /** Re-runs the verse-attribute loader for both splits (called after edits). */
    fun reloadBothAttributeMaps()

    /** Switches the primary version (used by "Compare" to jump to a selected version). */
    fun loadVersion(mv: MVersion)

    /** Enters dictionary mode for the given set of ARIs. */
    fun startDictionaryMode(aris: Set<Int>)

    /** Returns whether the current version(s) are eligible for Ribka reporting. */
    fun checkRibkaEligibility(): RibkaEligibility

    /** Current human-recording coverage/failure state for the displayed chapter. */
    fun recordedAudioAvailability(): RecordedAudioAvailability

    /** Opens the audio bar and starts playback at the given 1-based verse. */
    fun playAudioFromVerse(verse_1: Int)

    /** Reads the selected verses using the explicitly pinned Google TTS fallback. */
    fun speakSelectedVerses(selectedVerses1: IntArrayList)

    /** Offers, but never automatically starts, TTS after recorded narration failed. */
    fun offerTtsAfterRecordedFailure(selectedVerses1: IntArrayList)

    /** Called from `onDestroyActionMode` so the Activity can clear its `actionMode` field. */
    fun onActionModeDestroyed()
}
