package yuku.alkitab.songs

import android.content.Context
import android.text.TextUtils
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.afollestad.materialdialogs.MaterialDialog
import java.io.IOException
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.R

private const val TAG = "ExoplayerController"

/**
 * We will use [MidiController] for MIDI files.
 */
class ExoplayerController(appContext: Context) : MediaController() {
    private var mp = ExoPlayer.Builder(appContext).build()
    private var callback: ExoplayerCallback? = null
    private var audioUrl: String? = null

    override fun reset() {
        super.reset()
        mp.stop()
    }

    fun setCallback(callback: ExoplayerCallback) {
        this.callback = callback
    }

    fun setAudioUrl(url: String) {
        this.audioUrl = url
    }

    @OptIn(UnstableApi::class)
    override fun playOrPause(playInLoop: Boolean) {
        AppLog.d(TAG, "playOrPause called, playInLoop: $playInLoop, state: $state")

        when (state) {
            State.reset -> { }

            State.reset_media_known_to_exist, State.complete, State.error -> {
                try {
                    state = State.preparing

                    val selectedUrl = audioUrl ?: url.toString()
                    AppLog.d(TAG, "Selected URL: $selectedUrl")

                    val mediaSource = createMediaSource(selectedUrl)
                    AppLog.d(TAG, "Media source created")

                    mediaPlayerPrepare(mediaSource, playInLoop)
                } catch (e: IOException) {
                    AppLog.e(TAG, "buffering to local cache", e)
                    state = State.error
                }
            }

            State.preparing -> { }

            State.playing -> {
                AppLog.d(TAG, "State: playing - Pausing playback")
                if (playInLoop) {
                    mp.repeatMode = Player.REPEAT_MODE_ONE
                } else {
                    mp.playWhenReady = false
                    state = State.paused
                    callback?.onPlayerStateChanged(false)
                }
            }

            State.paused -> {
                AppLog.d(TAG, "State: paused - Resuming playback")
                mp.repeatMode = if (playInLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                mp.playWhenReady = true
                state = State.playing
                callback?.onPlayerStateChanged(true)
            }
        }.let {}
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    // only start playing if the current state is preparing, i.e., not error or reset.
                    AppLog.d(TAG, "@@onPlayerStateChanged STATE_READY")
                    if (state == State.preparing) {
                        mp.playWhenReady = true
                        state = State.playing
                        callback?.onPlayerStateChanged(true)
                    }
                }

                Player.STATE_ENDED -> {
                    AppLog.d(TAG, "@@onPlayerStateChanged STATE_ENDED repeatMode=" + mp.repeatMode)
                    state = State.complete
                    callback?.onPlayerStateChanged(false)
                }

                else -> { }
            }
        }

        @OptIn(UnstableApi::class)
        override fun onPlayerError(error: PlaybackException) {
            AppLog.e(TAG, "@@onPlayerError error=$error")
            val activity = activityRef?.get()
            if (activity != null && !activity.isFinishing) {
                val errorType = when (error) {
                    is ExoPlaybackException -> error.type
                    else -> null
                }

                // https://stackoverflow.com/a/42996915/11238
                val innerException = when (error) {
                    is ExoPlaybackException -> when (error.type) {
                        ExoPlaybackException.TYPE_SOURCE -> error.sourceException
                        ExoPlaybackException.TYPE_RENDERER -> error.rendererException
                        ExoPlaybackException.TYPE_UNEXPECTED -> error.unexpectedException
                        else -> null
                    }

                    else -> null
                }

                MaterialDialog(activity).show {
                    message(text = TextUtils.expandTemplate(activity.getString(R.string.song_player_error_description), "$errorType $innerException"))
                    positiveButton(R.string.ok)
                }
            }
            state = State.error
        }
    }

    @OptIn(UnstableApi::class)
    private fun mediaPlayerPrepare(mediaSource: MediaSource, playInLoop: Boolean) {
        try {
            state = State.preparing

            mp.addListener(playerListener)

            // Prepare the player with the source.
            mp.repeatMode = if (playInLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            mp.setMediaSource(mediaSource)
            mp.prepare()
        } catch (e: IOException) {
            AppLog.e(TAG, "mp setDataSource", e)
            state = State.error
        }
    }

    @OptIn(UnstableApi::class)
    private fun createMediaSource(url: String): MediaSource {
        val uri = url.toUri()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Connections.httpUserAgent)

        return if (url.endsWith(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
        }
    }

    /**
     * @return current position and duration in ms. Any of them can be -1 if unknown.
     */
    override fun getProgress(): LongArray = when (state) {
        State.playing, State.paused, State.complete -> {
            val position = try {
                mp.currentPosition
            } catch (e: Exception) {
                AppLog.e(TAG, "@@getProgress getCurrentPosition", e)
                -1L
            }

            val duration = try {
                mp.duration
            } catch (e: Exception) {
                AppLog.e(TAG, "@@getProgress getDuration", e)
                -1L
            }

            longArrayOf(position, duration)
        }

        else -> longArrayOf(-1, -1)
    }

    fun setPlaybackSpeed(speed: Float) {
        val parameters = mp.playbackParameters.withSpeed(speed)
        mp.playbackParameters = parameters
    }

    fun getPlaybackSpeed(): Float {
        return mp.playbackParameters.speed
    }

    interface ExoplayerCallback {
        fun onPlayerStateChanged(isPlaying: Boolean)
    }
}
