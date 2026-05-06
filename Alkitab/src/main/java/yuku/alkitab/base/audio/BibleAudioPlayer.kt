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

        ExoPlayer.Builder(
            appContext,
            audioOnlyRenderersFactory,
            ProgressiveMediaSource.Factory(okHttpDataSourceFactory, mp3ExtractorFactory),
        )
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
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

    @MainThread
    fun play() {
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
