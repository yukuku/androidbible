package yuku.alkitab.base.audio

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.OptIn
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
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.util.AppLog

private const val TAG = "BibleAudioPlayer"

/**
 * Wraps a single ExoPlayer instance for Bible chapter audio playback.
 * Must be created, used, and released on the main thread.
 * Distinct from [yuku.alkitab.songs.ExoplayerController] so that song playback is unaffected.
 */
@OptIn(UnstableApi::class)
class BibleAudioPlayer(appContext: Context) {

    interface Listener {
        fun onReady()
        fun onEnded()
        fun onError(message: String)
    }

    var listener: Listener? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    AppLog.d(TAG, "STATE_READY")
                    listener?.onReady()
                }

                Player.STATE_ENDED -> {
                    AppLog.d(TAG, "STATE_ENDED")
                    listener?.onEnded()
                }

                else -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLog.e(TAG, "onPlayerError: $error")
            listener?.onError(error.message ?: error.javaClass.simpleName)
        }
    }

    private val player: ExoPlayer = run {
        val audioOnlyFactory = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf<Renderer>(
                MediaCodecAudioRenderer(appContext, MediaCodecSelector.DEFAULT, handler, audioListener)
            )
        }
        val mp3Factory = ExtractorsFactory { arrayOf(Mp3Extractor()) }
        val dataSourceFactory = OkHttpDataSource.Factory(Connections.okHttp)
            .setUserAgent(Connections.httpUserAgent)

        ExoPlayer.Builder(appContext, audioOnlyFactory, ProgressiveMediaSource.Factory(dataSourceFactory, mp3Factory))
            .build()
            .also { it.addListener(playerListener) }
    }

    val currentPositionMs: Long get() = player.currentPosition
    val isPlaying: Boolean get() = player.isPlaying

    @MainThread
    fun load(url: String) {
        player.stop()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        AppLog.d(TAG, "load url=$url")
    }

    @MainThread
    fun play() {
        player.playWhenReady = true
    }

    @MainThread
    fun pause() {
        player.playWhenReady = false
    }

    @MainThread
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    @MainThread
    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    @MainThread
    fun setRepeat(repeat: Boolean) {
        player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Release all ExoPlayer resources. Do not use this instance after calling release(). */
    @MainThread
    fun release() {
        player.release()
    }
}
