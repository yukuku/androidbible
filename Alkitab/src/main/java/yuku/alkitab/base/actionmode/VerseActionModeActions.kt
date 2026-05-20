package yuku.alkitab.base.actionmode

import yuku.alkitab.base.model.MVersion

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

    /** Whether the "play audio from this verse" item should be offered. */
    fun isAudioAvailableForVerseAction(): Boolean

    /** Opens the audio bar and starts playback at the given 1-based verse. */
    fun playAudioFromVerse(verse_1: Int)

    /** Called from `onDestroyActionMode` so the Activity can clear its `actionMode` field. */
    fun onActionModeDestroyed()
}
