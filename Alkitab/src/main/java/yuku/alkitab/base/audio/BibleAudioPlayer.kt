package yuku.alkitab.base.audio

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.debug.R

/**
 * Wrapper around media3 [ExoPlayer] for Bible chapter audio. Builds an
 * audio-only, MP3-only player that fetches over [Connections.okHttp] (for its
 * user-agent and disk cache) and forwards state transitions to a [Listener].
 *
 * Out of scope:
 *  - Foreground-service / MediaSession lifecycle, owned by [BibleAudioService].
 *  - Highlight / verse tracking, owned by [HighlightTracker].
 *  - Audio focus and becoming-noisy, which media3 handles from the
 *    [ExoPlayer.Builder.setAudioAttributes] and
 *    [ExoPlayer.Builder.setHandleAudioBecomingNoisy] configuration below.
 *
 * Threading: every public method must be called on the main thread, the same
 * constraint media3 imposes on [Player].
 *
 * Mirrors [yuku.alkitab.songs.ExoplayerController]'s data-source and renderer
 * configuration without extending it, because songs and bible audio have
 * different state machines and different error UX.
 *
 * [onLogEvent] receives a trace of what the load is actually doing: every
 * OkHttp connection-state transition for the chapter fetch (via
 * [AudioHttpEventLogger]) plus this player's own buffering/ready/error
 * transitions. [BibleAudioService] feeds this into [PlaybackState.logs] for the
 * audio bar's status line and log bottom sheet. Each event carries the
 * [logGeneration] it belongs to so the service can drop stale ones.
 */
@OptIn(UnstableApi::class)
class BibleAudioPlayer(
    appContext: Context,
    private val onLogEvent: (generation: Int, message: AudioLogMessage) -> Unit,
) {

    /**
     * Identifies the chapter load that subsequent log events belong to. The
     * service bumps this before swapping the media item.
     *
     * Loading a new chapter cancels the outgoing one's in-flight HTTP calls,
     * and OkHttp reports that cancellation through the *old* calls' event
     * listeners, which fire after the new load has already reset the log.
     * Stamping each call with the generation current when it was created lets
     * the service tell those teardown events apart from the new chapter's own.
     *
     * Volatile because calls are created on media3's loader threads while the
     * service writes this from the main thread.
     */
    @Volatile
    var logGeneration: Int = 0

    interface Listener {
        /**
         * Player is loading data: initial buffering after [prepare], or
         * re-buffering after a [seekTo] target that wasn't already cached. The
         * service uses this to flip the bar's `preparing` flag back to true so
         * the spinner returns when the user seeks ahead and presses play.
         */
        fun onBuffering()

        /** Player has buffered enough to start playback. */
        fun onReady()

        /** Player reached the end of the chapter (natural end, not pause). */
        fun onEnded()

        /** Player hit a fatal error; the underlying player is now in an error state. */
        fun onError(error: PlaybackException)
    }

    private var listener: Listener? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    onLogEvent(logGeneration, AudioLogMessage(R.string.audio_log_player_buffering))
                    listener?.onBuffering()
                }
                Player.STATE_READY -> {
                    onLogEvent(logGeneration, AudioLogMessage(R.string.audio_log_player_ready))
                    listener?.onReady()
                }
                Player.STATE_ENDED -> {
                    onLogEvent(logGeneration, AudioLogMessage(R.string.audio_log_player_ended))
                    listener?.onEnded()
                }
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val code = stripErrorCodePrefix(error.errorCodeName)
            val detail = error.message
            onLogEvent(
                logGeneration,
                if (detail != null) {
                    AudioLogMessage(R.string.audio_log_player_error_detail, listOf(code, detail))
                } else {
                    AudioLogMessage(R.string.audio_log_player_error, listOf(code))
                }
            )
            listener?.onError(error)
        }
    }

    val exoPlayer: ExoPlayer = run {
        val audioOnlyRenderersFactory = RenderersFactory { eventHandler, videoRendererEventListener, audioRendererEventListener, _, _ ->
            arrayOf<Renderer>(
                MediaCodecAudioRenderer(
                    appContext,
                    MediaCodecSelector.DEFAULT,
                    eventHandler,
                    audioRendererEventListener,
                )
            )
        }
        val mp3ExtractorFactory = ExtractorsFactory {
            arrayOf<Extractor>(Mp3Extractor())
        }
        // A client scoped to this player, not the app-wide Connections.okHttp,
        // so the event logging only ever fires for chapter audio fetches.
        // newBuilder() still shares the connection pool and disk cache with the
        // shared client.
        val loggingOkHttpClient = Connections.okHttp.newBuilder()
            .eventListenerFactory {
                // Snapshot the generation when the call is created, not when
                // each event fires: a call belongs to whichever chapter load
                // started it, and its cancellation events arrive after the
                // next load has already bumped [logGeneration].
                val generation = logGeneration
                AudioHttpEventLogger { message -> onLogEvent(generation, message) }
            }
            .build()
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(loggingOkHttpClient)
            .setUserAgent(Connections.httpUserAgent)

        // USAGE_MEDIA with CONTENT_TYPE_SPEECH gives the right ducking behavior
        // for spoken-word audio: other apps' notifications duck us politely,
        // calls pause us. `handleAudioFocus = true` (below) flips on media3's
        // built-in focus request: pause on transient loss, duck on can-duck,
        // resume on regain.
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        // ExoPlayer's default LoadControl keeps zero back buffer and only ~50 s
        // of forward buffer, so prev-verse seeks (and any rewind past the
        // currently-playing window) re-fetch and re-decode the MP3 from the
        // network. Bible chapters are short (typically under 30 min, ~30 MB at
        // 128 kbps) and the nav UX taps prev/next verse aggressively, so it is
        // worth keeping a whole chapter in the player's sample queue once
        // buffered. The numbers below are picked for that scrub-around workflow:
        //  - 30 min of back buffer covers the longest chapter (Psalm 119) and
        //    keeps every previously-played verse instantly seekable.
        //  - maxBufferMs of 30 min lets the player keep loading well past the
        //    comfort zone, so next-verse taps land in already-buffered samples
        //    even if the user races ahead of the playhead.
        //  - minBufferMs stays at its default: it is only the eager-loading
        //    comfort threshold and does NOT gate playback start. That is
        //    bufferForPlaybackMs, which is also left alone.
        //  - retainBackBufferFromKeyframe = true: MP3 has a keyframe per frame,
        //    so this keeps all samples until the back buffer wraps.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                /* maxBufferMs = */ 30 * 60_000,
                /* bufferForPlaybackMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                /* bufferForPlaybackAfterRebufferMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 30 * 60_000,
                /* retainBackBufferFromKeyframe = */ true,
            )
            // Unset so the byte budget is derived from the durations above
            // rather than an explicit cap overriding them.
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()

        ExoPlayer.Builder(
            appContext,
            audioOnlyRenderersFactory,
            ProgressiveMediaSource.Factory(okHttpDataSourceFactory, mp3ExtractorFactory),
        )
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .build()
            .also { it.addListener(playerListener) }
    }

    @MainThread
    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    /**
     * Replaces the current media item with [url] and arms playback to start as
     * soon as buffering completes.
     */
    @MainThread
    fun prepare(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    /**
     * Starts (or resumes) playback. At [Player.STATE_ENDED] this seeks back to
     * the start so tapping play replays the chapter, matching how native media
     * apps treat the play button at end-of-stream.
     */
    @MainThread
    fun play() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L)
        }
        exoPlayer.playWhenReady = true
    }

    @MainThread
    fun pause() {
        exoPlayer.playWhenReady = false
    }

    @MainThread
    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    @MainThread
    fun setSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    val isPlaying: Boolean
        @MainThread
        get() = exoPlayer.isPlaying

    val currentPositionMs: Long
        @MainThread
        get() = exoPlayer.currentPosition.coerceAtLeast(0L)

    val durationMs: Long
        @MainThread
        get() = exoPlayer.duration.let { if (it < 0L) 0L else it }

    val speed: Float
        @MainThread
        get() = exoPlayer.playbackParameters.speed

    /** Releases the underlying ExoPlayer. After this call the instance is unusable. */
    @MainThread
    fun release() {
        exoPlayer.removeListener(playerListener)
        listener = null
        exoPlayer.release()
    }
}
