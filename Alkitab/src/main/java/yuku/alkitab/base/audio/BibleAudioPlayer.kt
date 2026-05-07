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

/**
 * Single-responsibility wrapper around media3 [ExoPlayer] for Bible chapter audio.
 *
 * Responsibilities:
 *  - Build an audio-only ExoPlayer that fetches over [Connections.okHttp] (for
 *    user-agent + 50 MB disk cache) and decodes MP3 only (we never serve other
 *    formats from the audio backend).
 *  - Forward state transitions to a [Listener]: [Listener.onReady] when the
 *    chapter is buffered enough to play, [Listener.onEnded] when playback hits
 *    the end of the stream, [Listener.onError] on any [PlaybackException].
 *
 * Out of scope:
 *  - Foreground-service / MediaSession lifecycle — owned by [BibleAudioService].
 *  - Highlight / verse tracking — owned by [HighlightTracker].
 *  - Audio focus / becoming-noisy — handled inside this class via
 *    [ExoPlayer.Builder.setAudioAttributes] +
 *    [ExoPlayer.Builder.setHandleAudioBecomingNoisy] (built-in media3 behavior).
 *
 * Threading: every public method must be called on the main thread; this is the
 * same constraint media3 itself imposes on [Player]. The internal listener also
 * dispatches to the main thread because that's where ExoPlayer lives.
 *
 * Mirrors [yuku.alkitab.songs.ExoplayerController]'s data-source / renderer
 * configuration (audio-only renderer, MP3-only extractor, OkHttp-backed
 * data-source) but does not extend it — songs and bible audio have different
 * state machines and different error UX.
 */
@OptIn(UnstableApi::class)
class BibleAudioPlayer(appContext: Context) {

    interface Listener {
        /**
         * Player is loading data — either initial buffering after [prepare], or
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
                Player.STATE_BUFFERING -> listener?.onBuffering()
                Player.STATE_READY -> listener?.onReady()
                Player.STATE_ENDED -> listener?.onEnded()
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
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
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(Connections.okHttp)
            .setUserAgent(Connections.httpUserAgent)

        // USAGE_MEDIA + CONTENT_TYPE_SPEECH gives the right ducking behavior for
        // spoken-word audio (other apps' notifications duck us politely; calls
        // pause us). `handleAudioFocus = true` flips on media3's built-in focus
        // request — pause on transient loss, duck on can-duck, resume on regain.
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        // ExoPlayer's default LoadControl keeps zero back buffer and only ~50 s
        // of forward buffer, so prev-verse seeks (and any rewind past the
        // currently-playing window) re-fetch and re-decode the MP3 from the
        // network. Bible chapters are short (typical ≤ 30 min, ~30 MB at
        // 128 kbps) and our nav UX taps prev/next verse aggressively, so it's
        // worth keeping the entire chapter in the player's sample queue once
        // it's been buffered.
        //
        // Numbers below are picked for a "play-and-scrub-around" workflow:
        //  - back buffer ~30 min covers Psalms 119 (the longest chapter) and
        //    keeps every previously-played verse instantly seekable.
        //  - forward min/max bumped to 5 min / 30 min so a fresh load fetches
        //    enough up-front that next-verse taps land in already-buffered
        //    samples.
        //  - retainBackBufferFromKeyframe = true: MP3 has a keyframe per
        //    frame, so this is essentially "keep all PCM samples until the
        //    back buffer wraps".
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 5 * 60_000,
                /* maxBufferMs = */ 30 * 60_000,
                /* bufferForPlaybackMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                /* bufferForPlaybackAfterRebufferMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 30 * 60_000,
                /* retainBackBufferFromKeyframe = */ true,
            )
            // -1 = pick a target byte budget per renderer based on the
            // configured durations. We don't want an explicit byte cap to
            // override the duration-based one we just set.
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
     * Loads [url] as the current media item, prepares the player, and arms
     * playback to start as soon as buffering completes. Replaces any previous
     * media item.
     */
    @MainThread
    fun prepare(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    /**
     * Starts (or resumes) playback. If the player has already finished the
     * current chapter ([Player.STATE_ENDED]), this seeks back to the start
     * so tapping play again replays the chapter from the beginning, matching
     * how native media apps treat the play button at end-of-stream.
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
