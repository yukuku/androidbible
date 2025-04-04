package yuku.alkitab.songs

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.TimingUtil
import yuku.alkitab.base.util.toIntArray
import yuku.alkitab.base.verses.EmptyableRecyclerView
import yuku.alkitab.base.verses.VersesAdapter
import yuku.alkitab.debug.R
import yuku.alkitab.util.IntArrayList

private const val TAG = "ExoplayerController"

/**
 * We will use [MidiController] for MIDI files.
 */
class ExoplayerController(appContext: Context) : MediaController(), TimingUtil.HighlightListener {
    private var mp = ExoPlayer.Builder(appContext).build()
    private var callback: ExoplayerCallback? = null
    private var audioUrl: String? = null
    private lateinit var timingUtil: TimingUtil
    private var isAudioBarVisible = false

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

    fun initTimingUtil(timingUtil: TimingUtil) {
        this.timingUtil = timingUtil
    }

    fun setAudioBarVisible(visible: Boolean) {
        isAudioBarVisible = visible
    }

    @OptIn(UnstableApi::class)
    override fun playOrPause(playInLoop: Boolean) {
        AppLog.d(TAG, "playOrPause called, playInLoop: $playInLoop, state: $state")

        if (!isAudioBarVisible) {
            AppLog.d(TAG, "Audio bar is not visible, skipping playback")
            return
        }

        when (state) {
            State.reset, State.preparing -> return
            State.reset_media_known_to_exist, State.complete, State.error -> prepareAndPlay(playInLoop)
            State.playing -> pausePlayback(playInLoop)
            State.paused -> resumePlayback(playInLoop)
        }
    }

    private fun prepareAndPlay(playInLoop: Boolean) {
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

    private fun pausePlayback(playInLoop: Boolean) {
        AppLog.d(TAG, "Pausing playback")
        if (playInLoop) {
            mp.repeatMode = Player.REPEAT_MODE_ONE
        } else {
            mp.playWhenReady = false
            state = State.paused
            callback?.onPlayerStateChanged(false)
        }
    }

    private fun resumePlayback(playInLoop: Boolean) {
        AppLog.d(TAG, "Resuming playback")
        mp.repeatMode = if (playInLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        mp.playWhenReady = true
        state = State.playing
        callback?.onPlayerStateChanged(true)
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
                    callback?.onAudioEnded()
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
        val dataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(Connections.httpUserAgent)
        return if (url.endsWith(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
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

        else -> {
            //AppLog.d(TAG, "getProgress() - Exoplayer not playing, returning -1")
            longArrayOf(-1, -1)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mp.playbackParameters = mp.playbackParameters.withSpeed(speed)
    }

    fun getPlaybackSpeed(): Float = mp.playbackParameters.speed

    override fun applyHighlight(verseNumber: Int, color: Int) {
        AppLog.d(TAG, "applyHighlight called - verseNumber: $verseNumber, color: $color")

        val selectedVerses = IntArrayList().apply { add(verseNumber) }
        highlightVerses(selectedVerses, color)

        AppLog.d(TAG, "applyHighlight finished - highlight applied")
    }

    private fun highlightVerses(selectedVerses: IntArrayList, color: Int) {
        AppLog.d(TAG, "highlightVerses called - selectedVerses: $selectedVerses color: $color")
        for (verse in selectedVerses.toIntArray()) {
            updateVerseHighlightUI(verse, color)
        }
    }

    private fun updateVerseHighlightUI(verseNumber: Int, color: Int) {
        AppLog.d(TAG, "updateVerseHighlightUI called - verseNumber: $verseNumber, color: $color")

        val activity = activityRef?.get()  // Menggunakan activityRef dari MediaController
        Log.d(TAG, "updateVerseHighlightUI - activityref: $activityRef")
        if (activity is Activity) {
            Log.d(TAG, "updateVerseHighlightUI - activity: $activity")
            val recyclerView = activity.findViewById<EmptyableRecyclerView>(R.id.lsSplitView0)
            recyclerView?.adapter?.let { (it as? VersesAdapter)?.updateHighlight(verseNumber, color) }
        }

        AppLog.d(TAG, "updateVerseHighlightUI finished - highlight should be visible in UI")
    }

    /**
     Play Form Verse
     Function to play from a specific verse timing
     */

    fun playFromVerse(selectedVerse: Int, timingList: List<Triple<Long, Long, Int>>) {
        val verseTiming = timingList.find { it.third == selectedVerse }

        if (verseTiming == null) {
            Log.e(TAG, "playFromVerse - Selected verse not found in timing list")
            return
        }

        val startTime = verseTiming.first

        Log.d(TAG, "playFromVerse - startTime: $startTime, selectedVerse: $selectedVerse")
        CoroutineScope(Dispatchers.Main).launch {
            seekTo(startTime)
            playOrPause(true)
        }
    }

    fun seekTo(position: Long) {
        mp.seekTo(position)
        Log.d(TAG, "seekTo: $position")
    }

    interface ExoplayerCallback {
        fun onPlayerStateChanged(isPlaying: Boolean)
        fun onAudioEnded()
    }
}
