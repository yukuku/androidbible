package yuku.alkitab.songs

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
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

    override fun reset() {
        super.reset()
        mp.stop()
    }

    @OptIn(UnstableApi::class)
    override fun playOrPause(playInLoop: Boolean) {
        when (state) {
            State.reset -> {
            }

            State.reset_media_known_to_exist, State.complete, State.error -> {
                try {
                    state = State.preparing

                    // Produces DataSource instances through which media data is loaded.
                    val dataSourceFactory = OkHttpDataSource.Factory(Connections.okHttp)
                        .setUserAgent(Connections.httpUserAgent)

                    // This is the MediaSource representing the media to be played.
                    val mediaSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(url)))

                    mediaPlayerPrepare(mediaSource, playInLoop)
                } catch (e: IOException) {
                    AppLog.e(TAG, "buffering to local cache", e)
                    state = State.error
                }
            }

            State.preparing -> {
            }

            State.playing -> // pause button pressed
                if (playInLoop) { // looping play is selected, but we are already playing. So just set looping parameter.
                    mp.repeatMode = Player.REPEAT_MODE_ONE
                } else {
                    mp.playWhenReady = false
                    state = State.paused
                }

            State.paused -> {
                // play button pressed when paused
                mp.repeatMode = if (playInLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                mp.playWhenReady = true
                state = State.playing
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
                    }
                }

                Player.STATE_ENDED -> {
                    AppLog.d(TAG, "@@onPlayerStateChanged STATE_ENDED repeatMode=" + mp.repeatMode)
                    state = State.complete
                }

                else -> {
                }
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
}
