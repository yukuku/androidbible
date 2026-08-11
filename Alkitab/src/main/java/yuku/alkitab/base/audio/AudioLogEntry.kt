package yuku.alkitab.base.audio

/**
 * One timestamped entry in the audio bar's load log — HTTP connection-state
 * transitions plus player state transitions for the chapter currently
 * loading. Reset at the start of every [BibleAudioService.loadChapter] call,
 * so a retry starts a fresh log rather than appending to the failed attempt's.
 */
data class AudioLogEntry(
    val timestampMs: Long,
    val message: String,
)
