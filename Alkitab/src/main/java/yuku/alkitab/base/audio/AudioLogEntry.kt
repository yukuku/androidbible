package yuku.alkitab.base.audio

/**
 * One timestamped entry in the audio bar's load log: HTTP connection-state
 * transitions plus player state transitions for the current audio load.
 * Each load starts a fresh log, so a retry does not mix its events with the
 * failed attempt's.
 */
data class AudioLogEntry(
    val timestampMs: Long,
    val message: String,
)
