package yuku.alkitab.songs

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
class ExoplayerController(appContext: Context) : MediaController(){
    private var mp = ExoPlayer.Builder(appContext).build()
    private var callback: ExoplayerCallback? = null

    private var audioUrl0: String? = null
    private var audioUrl1: String? = null

    private var mediaSource0: MediaSource? = null
    private var mediaSource1: MediaSource? = null

    private lateinit var timingUtil: TimingUtil
    private var isAudioBarVisible = false

    var controllerId: Int = 0
    private var currentPlayJob: Job? = null

    private var currentAyatIndex = 0
    private var currentAudioIndex = 0 // 0 = audioUrl0, 1 = audioUrl1
    private var isAlternatingPlayActive = false

    override fun reset() {
        AppLog.d(TAG, "reset() called - clearing media items and listener")
        super.reset()
        mp.stop()
        mp.clearMediaItems()
        mp.removeListener(playerListener)
    }

    fun release() {
        AppLog.d(TAG, "release() called - releasing ExoPlayer resources")
        mp.removeListener(playerListener)
        mp.release()
    }

    fun setCallback(callback: ExoplayerCallback) {
        this.callback = callback
    }

    fun setAudioUrl(url: String) {
        val source = createMediaSource(url)
        if (controllerId == 0) {
            audioUrl0 = url
            mediaSource0 = source
            AppLog.d(TAG, "[0] setAudioUrl() - URL0 set to: $url")
        } else {
            audioUrl1 = url
            mediaSource1 = source
            AppLog.d(TAG, "[$controllerId] setAudioUrl() - URL1 set to: $url")
        }
    }

    fun initTimingUtil(timingUtil: TimingUtil) {
        this.timingUtil = timingUtil
        AppLog.d(TAG, "initTimingUtil() - TimingUtil assigned")
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

    internal fun prepareAndPlay(playInLoop: Boolean) {
        AppLog.d(TAG, "[$controllerId] prepareAndPlay dipanggil, mediaSource0=$mediaSource0, mediaSource1=$mediaSource1")
        try {
            AppLog.d(TAG, "prepareAndPlay() - preparing media")
            state = State.preparing

            val mediaSource = when (controllerId) {
                0 -> mediaSource0
                1 -> mediaSource1
                else -> null
            } ?: run {
                AppLog.e(TAG, "No mediaSource available for controllerId=$controllerId")
                state = State.error
                return
            }

            AppLog.d(TAG, "[$controllerId] prepareAndPlay() - Using mediaSource: ${mediaSource::class.java.simpleName}")
            mediaPlayerPrepare(mediaSource, playInLoop)
        } catch (e: IOException) {
            AppLog.e(TAG, "buffering to local cache", e)
            state = State.error
        }
    }

    fun pausePlayback(playInLoop: Boolean) {
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
                Player.STATE_BUFFERING -> {
                    AppLog.d(TAG, "[$controllerId] 🎵 ExoPlayer BUFFERING pos=${mp.currentPosition}")
                }

                Player.STATE_READY -> {
                    // only start playing if the current state is preparing, i.e., not error or reset.
                    AppLog.d(TAG, "[$controllerId] 🎵 ExoPlayer READY pos=${mp.currentPosition} dur=${mp.duration} state=$state")
                    if (state == State.preparing) {
                        mp.play()
                        state = State.playing
                        callback?.onPlayerStateChanged(true)
                    }
                }

                Player.STATE_ENDED -> {
                    AppLog.d(TAG, "[$controllerId] 🎵 ExoPlayer ENDED pos=${mp.currentPosition} dur=${mp.duration}")
                    state = State.complete
                    callback?.onPlayerStateChanged(false)
                    callback?.onAudioEnded()
                }

                else -> {}
            }
        }

        @OptIn(UnstableApi::class)
        override fun onPlayerError(error: PlaybackException) {
            state = State.error
            AppLog.e(TAG, "[$controllerId] ❌ onPlayerError: ${error.message}")
            val activity = activityRef?.get()
            if (activity != null && !activity.isFinishing) {
                val errorType = (error as? ExoPlaybackException)?.type
                val innerException = when (errorType) {
                    ExoPlaybackException.TYPE_SOURCE -> error.sourceException
                    ExoPlaybackException.TYPE_RENDERER -> error.rendererException
                    ExoPlaybackException.TYPE_UNEXPECTED -> error.unexpectedException
                    else -> null
                }
                MaterialDialog(activity).show {
                    message(text = TextUtils.expandTemplate(activity.getString(R.string.song_player_error_description), "$errorType $innerException"))
                    positiveButton(R.string.ok)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun mediaPlayerPrepare(mediaSource: MediaSource, playInLoop: Boolean) {
        try {
            AppLog.d(TAG, "mediaPlayerPrepare() - Preparing player with media source, playInLoop: $playInLoop")

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
            longArrayOf(mp.currentPosition, mp.duration)
        }

        else -> longArrayOf(-1, -1)
    }

    fun setPlaybackSpeed(speed: Float) {
        mp.playbackParameters = mp.playbackParameters.withSpeed(speed)
    }

    fun getPlaybackSpeed(): Float = mp.playbackParameters.speed

    fun highlightVerses(selectedVerses: IntArrayList, color: Int) {
        AppLog.d(TAG, "highlightVerses called - selectedVerses: $selectedVerses color: $color")
        for (verse in selectedVerses.toIntArray()) {
            updateVerseHighlightUI(verse, color)
        }
    }

    private fun updateVerseHighlightUI(verseNumber: Int, color: Int) {
        AppLog.d(TAG, "Highlighting verse $verseNumber with color $color")

        activityRef?.get()?.let { activity ->
            listOf(R.id.lsSplitView0, R.id.lsSplitView1).forEach { viewId ->
                (activity.findViewById<EmptyableRecyclerView>(viewId)?.adapter as? VersesAdapter)
                    ?.updateHighlight(verseNumber, color)
            }
        }
    }

    /**
     Play Form Verse
     Function to play from a specific verse timing
     */

    fun playFromVerse(selectedVerse: Int, timingList: List<Triple<Long, Long, Int>>, withEnd: Boolean = false) {
        val verseTiming = timingList.find { it.third == selectedVerse }
        if (verseTiming == null) {
            Log.e(TAG, "playFromVerse - Selected verse not found in timing list")
            return
        }

        val (startTime, endTime) = verseTiming
        val duration = endTime - startTime

        Log.d(TAG, "playFromVerse - startTime: $startTime, endTime: $endTime, selectedVerse: $selectedVerse")

        currentPlayJob?.cancel()

        currentPlayJob = CoroutineScope(Dispatchers.Main).launch {
            seekTo(startTime)
            playOrPause(true)

            if (withEnd) {
                delay(duration)
                callback?.onAudioEnded()
                Log.d(TAG, "Triggering onAudioEnded() manually after delay")
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun playPrimaryAudio(verse: Int) {
        if (mediaSource0 == null) {
            Log.e(TAG, "playPrimaryAudio() gagal: mediaSource0 belum diset")
            return
        }

        Log.d(TAG, "playPrimaryAudio() untuk ayat $verse")

        mp.stop()
        mp.setMediaSource(mediaSource0!!)
        mp.prepare()
        mp.seekTo(0) // mulai dari awal
        mp.play()
    }

    @OptIn(UnstableApi::class)
    fun playSecondaryAudio(verse: Int) {
        if (mediaSource1 == null) {
            Log.e(TAG, "playSecondaryAudio() gagal: mediaSource1 belum diset")
            return
        }

        Log.d(TAG, "playSecondaryAudio() untuk ayat $verse")

        mp.stop()
        mp.setMediaSource(mediaSource1!!)
        mp.prepare()
        mp.seekTo(0)
        mp.play()
    }


    fun seekTo(position: Long) {
        mp.seekTo(position)
        Log.d(TAG, "seekTo: $position")
    }

    fun playSegment(startTime: Long, endTime: Long, onComplete: () -> Unit) {
        currentPlayJob?.cancel()
        currentPlayJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                AppLog.d(TAG, "[$controllerId] playSegment start=$startTime end=$endTime state=$state isPlaying=${mp.isPlaying}")

                if (state == State.reset || state == State.error || !mp.isPlaying) {
                    AppLog.d(TAG, "[$controllerId] playSegment: state=$state → prepareAndPlay() sebelum seekTo")
                    prepareAndPlay(false)
                }

                seekTo(startTime)
                playOrPause(true)
                delay(endTime - startTime)
                playOrPause(false)
                AppLog.d(TAG, "[$controllerId] ⏹️ playSegment selesai")
                onComplete()
            } catch (e: Exception) {
                AppLog.e(TAG, "[$controllerId] playSegment error: ${e.message}")
                playOrPause(false)
                onComplete()
            }
        }
    }

    fun stopSegment() {
        AppLog.d(TAG, "[$controllerId] stopSegment() dipanggil")
        currentPlayJob?.cancel()
        if (mp.isPlaying) {
            mp.pause()
        }
        state = State.paused
        callback?.onPlayerStateChanged(false)
    }

    interface ExoplayerCallback {
        fun onPlayerStateChanged(isPlaying: Boolean)
        fun onAudioEnded()
    }
}
