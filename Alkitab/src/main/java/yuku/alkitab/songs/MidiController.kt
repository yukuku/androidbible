package yuku.alkitab.songs

import android.media.MediaPlayer
import android.os.Handler
import com.afollestad.materialdialogs.MaterialDialog
import yuku.alkitab.base.App
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Background
import yuku.alkitab.debug.R
import java.io.File
import java.io.FileInputStream
import java.io.IOException

private const val TAG = "MidiController"

/**
 * We will use [ExoplayerController] for non-MIDI files.
 * One of the reason is that Exoplayer allows us to use OkHttp as the downloader.
 */
class MidiController : MediaController() {
    private val mp = MediaPlayer()

    override fun reset() {
        super.reset()
        mp.reset()
    }

    override fun playOrPause(playInLoop: Boolean) {
        when (state) {
            State.reset -> {
            }
            State.reset_media_known_to_exist, State.complete, State.error -> {
                val handler = Handler()
                Background.run {
                    try {
                        state = State.preparing
                        val bytes = App.downloadBytes(url)
                        val cacheFile = File(App.context.cacheDir, "song_player_local_cache.mid")
                        cacheFile.writeBytes(bytes)

                        // this is a background thread. We must go back to main thread, and check again if state is OK to prepare.
                        handler.post {
                            if (state == State.preparing) { // the following should be synchronous, since we are loading from local.
                                mediaPlayerPrepare(cacheFile.absolutePath, playInLoop)
                            } else {
                                AppLog.d(TAG, "wrong state after downloading song file: $state")
                            }
                        }
                    } catch (e: IOException) {
                        AppLog.e(TAG, "buffering to local cache", e)
                        state = State.error
                    }
                }
            }
            State.preparing -> {
            }
            State.playing -> // pause button pressed
                if (playInLoop) { // looping play is selected, but we are already playing. So just set looping parameter.
                    mp.isLooping = true
                } else {
                    mp.pause()
                    state = State.paused
                }
            State.paused -> {
                // play button pressed when paused
                mp.isLooping = playInLoop
                mp.start()
                state = State.playing
            }
        }.let {}
    }

    private fun mediaPlayerPrepare(localFilename: String, playInLoop: Boolean) {
        try {
            state = State.preparing

            mp.setOnPreparedListener { player: MediaPlayer ->
                // only start playing if the current state is preparing, i.e., not error or reset.
                if (state == State.preparing) {
                    player.isLooping = playInLoop
                    player.start()
                    state = State.playing
                }
            }

            mp.setOnCompletionListener { player: MediaPlayer ->
                AppLog.d(TAG, "@@onCompletion looping=" + player.isLooping)
                if (!player.isLooping) {
                    player.reset()
                    state = State.complete
                }
            }

            mp.setOnErrorListener { _: MediaPlayer?, what: Int, extra: Int ->
                AppLog.e(TAG, "@@onError controller_state=$state what=$what extra=$extra")
                if (state != State.reset) { // Errors can happen if we call MediaPlayer#reset when MediaPlayer state is Preparing. In this case, do not show error message.
                    val activity = activityRef?.get()
                    if (activity != null && !activity.isFinishing) {
                        MaterialDialog.Builder(activity)
                            .content(activity.getString(R.string.song_player_error_description, what, extra))
                            .positiveText(R.string.ok)
                            .show()
                    }
                }
                state = State.error
                false // let OnCompletionListener be called.
            }

            val fis = FileInputStream(localFilename)
            val fd = fis.fd
            mp.reset()
            mp.setDataSource(fd)
            fis.close()
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
        State.playing, State.paused -> {
            val position = try {
                mp.currentPosition.toLong()
            } catch (e: Exception) {
                AppLog.e(TAG, "@@getProgress getCurrentPosition", e)
                -1L
            }

            val duration = try {
                mp.duration.toLong()
            } catch (e: Exception) {
                AppLog.e(TAG, "@@getProgress getDuration", e)
                -1L
            }

            longArrayOf(position, duration)
        }

        else -> longArrayOf(-1, -1)
    }
}
