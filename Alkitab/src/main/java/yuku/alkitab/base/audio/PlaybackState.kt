package yuku.alkitab.base.audio

/**
 * UI-facing snapshot of [BibleAudioService]'s state. Emitted via the service's
 * `StateFlow<PlaybackState>` and consumed by the M3 audio bar.
 *
 *  - [isPlaying]   — true while the player is actually playing audio (not just
 *                    "play was requested but still buffering"; that's [preparing]).
 *  - [preparing]   — true between `loadChapter` and the player firing READY.
 *                    Drives the bar's play-button progress ring.
 *  - [versionId]   — versionId of the source currently loaded, or `""` when
 *                    nothing is loaded. Scopes the verse highlight to panes
 *                    whose version matches.
 *  - [audioId]     — recording identifier of the loaded audio set, or `""`
 *                    when nothing is loaded. Lets the bar render the active
 *                    recording and reconstruct the selected set after process
 *                    death or a service-initiated change.
 *  - [bookId]      — bookId of the chapter currently loaded into the service,
 *                    or `-1` when nothing is loaded. Lets [AudioBarController]
 *                    detect service-initiated chapter changes (e.g. lock-screen
 *                    skip) and propagate them back into `IsiActivity`.
 *  - [chapter_1]   — 1-based chapter of the loaded chapter; `0` when nothing is
 *                    loaded.
 *  - [verse_1]     — currently active 1-based verse, or `0` when no verse is
 *                    active (chapter intro, gap between verses, or no timing).
 *  - [positionMs]  — last polled [androidx.media3.common.Player.getCurrentPosition].
 *  - [durationMs]  — last polled [androidx.media3.common.Player.getDuration],
 *                    `0` until the player reports a real duration.
 *  - [speed]       — current playback speed, mirroring `PlaybackParameters.speed`.
 *  - [error]       — non-null when the player or repository hit an error this
 *                    session; the UI snackbars and offers retry. Cleared on
 *                    the next successful `loadChapter`.
 */
data class PlaybackState(
    val isPlaying: Boolean,
    val preparing: Boolean,
    val versionId: String,
    val audioId: String,
    val bookId: Int,
    val chapter_1: Int,
    val verse_1: Int,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val error: String?,
) {
    /**
     * True while a chapter is loaded into the service (playing, paused, or
     * buffering) — i.e. not [IDLE]/stopped. Drives the auto-reshow decision in
     * [AudioBarController] when the activity is recreated or returns from the
     * background. Mirrors the `bookId >= 0` invariant that `loadChapter` sets
     * and `stop()` clears.
     */
    val isActive: Boolean
        get() = bookId >= 0

    companion object {
        val IDLE = PlaybackState(
            isPlaying = false,
            preparing = false,
            versionId = "",
            audioId = "",
            bookId = -1,
            chapter_1 = 0,
            verse_1 = 0,
            positionMs = 0L,
            durationMs = 0L,
            speed = 1.0f,
            error = null,
        )
    }
}
