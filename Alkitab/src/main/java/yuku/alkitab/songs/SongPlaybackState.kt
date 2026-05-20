package yuku.alkitab.songs

/**
 * UI-facing snapshot of [SongAudioService]'s playback, emitted via a `StateFlow`
 * and mapped by [SongAudioController] onto the legacy [MediaController.State]
 * machine that drives [SongViewActivity]'s toolbar.
 *
 *  - [hasMedia]   — a song URL has been loaded into the player.
 *  - [preparing]  — between `load` and the player reporting READY.
 *  - [isPlaying]  — the player is actually producing audio.
 *  - [ended]      — playback reached the end of a non-looping song.
 *  - [error]      — non-null when the player hit a fatal error this session.
 *  - [positionMs] / [durationMs] — last known transport values, `-1` if unknown.
 */
data class SongPlaybackState(
    val hasMedia: Boolean,
    val preparing: Boolean,
    val isPlaying: Boolean,
    val ended: Boolean,
    val error: String?,
    val positionMs: Long,
    val durationMs: Long,
) {
    companion object {
        val IDLE = SongPlaybackState(
            hasMedia = false,
            preparing = false,
            isPlaying = false,
            ended = false,
            error = null,
            positionMs = -1L,
            durationMs = -1L,
        )
    }
}
