package yuku.alkitab.songs

import android.content.Context
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.afollestad.materialdialogs.MaterialDialog
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.model.MTiming
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.TimingUtil
import yuku.alkitab.base.util.toIntArray
import yuku.alkitab.debug.R
import yuku.alkitab.util.IntArrayList

private const val TAG = "ExoplayerController"

/**
 * We will use [MidiController] for MIDI files.
 */
@OptIn(UnstableApi::class)
class ExoplayerController(
    appContext: Context,
    private val scope: CoroutineScope
) : MediaController() {
    private lateinit var timingUtil: TimingUtil
    private var isAudioBarVisible = false
    private var audioUrl0: String? = null
    private var audioUrl1: String? = null

    private var mediaSource0: MediaSource? = null
    private var mediaSource1: MediaSource? = null
    private var callback: ExoplayerCallback? = null
    var controllerId: Int = 0
    private var currentPlayJob: Job? = null

    val mp by lazy {
        val audioOnlyRenderersFactory = RenderersFactory { eventHandler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput ->
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

        ExoPlayer.Builder(
            appContext,
            audioOnlyRenderersFactory,
            ProgressiveMediaSource.Factory(okHttpDataSourceFactory, mp3ExtractorFactory),
        ).build()
    }

    override fun reset() {
        super.reset()
        mp.stop()
    }

    override fun playOrPause(playInLoop: Boolean) {
        when (state) {
            State.reset -> {
            }

            State.reset_media_known_to_exist, State.complete, State.error -> {
                try {
                    state = State.preparing

                    val url = url
                    if (url != null) {
                        mediaPlayerPrepare(url, playInLoop)
                    }
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
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            AppLog.d(TAG, "🎬 onPlaybackStateChanged triggered: $playbackState")
            when (playbackState) {
                Player.STATE_READY -> {
                    // only start playing if the current state is preparing, i.e., not error or reset.
                    AppLog.d(TAG, "@@onPlayerStateChanged STATE_READY")
                    if (state == State.preparing) {
                        mp.playWhenReady = true
                        state = State.playing
                        callback?.onPlayerStateChanged(true)
                    }

                    scope.launch {
                        var lastPos = -1L
                        while (state == State.playing) {
                            delay(1000)
                            val pos = mp.currentPosition
                            val dur = mp.duration
                            AppLog.d(TAG, "🎧 Posisi: $pos / durasi: $dur repeat=${mp.repeatMode}")

                            // Jika durasi diketahui dan posisi sudah di akhir
                            if (dur > 0 && pos >= dur - 1000) {
                                AppLog.d(TAG, "✅ Playback mencapai akhir, panggil manual onAudioEnded()")
                                callback?.onAudioEnded()
                                break
                            }

                            // Jika posisi tidak berubah → kemungkinan playback macet / selesai
                            if (pos == lastPos) {
                                AppLog.d(TAG, "⚠️ Playback berhenti tanpa STATE_ENDED — panggil manual onAudioEnded()")
                                callback?.onAudioEnded()
                                break
                            }
                            lastPos = pos
                        }
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

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            AppLog.d(TAG, "onIsPlayingChanged: $isPlaying")
            callback?.onPlayerStateChanged(isPlaying) // 🔔 Beri tahu Activity
        }

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
            callback?.onPlayerStateChanged(false)
        }
    }

    fun mediaPlayerPrepare(url: String, playInLoop: Boolean) {
        try {
            state = State.preparing

            mp.addListener(playerListener)

            AppLog.d(TAG, "mediaPlayerPrepare() - addListener dipanggil, url=$url")

            mp.repeatMode = if (playInLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            mp.setMediaItem(MediaItem.fromUri(url))
            mp.prepare()
        } catch (e: IOException) {
            AppLog.e(TAG, "mp setDataSource", e)
            state = State.error
        }
    }

    /**
     * @return current position and duration in ms. Any of them can be -1 if unknown.
     */
    override fun getProgress(): LongArray {
        return try {
            var position = -1L
            var duration = -1L

            if (Looper.myLooper() == Looper.getMainLooper()) {
                position = mp.currentPosition
                duration = mp.duration
            } else {
                runBlocking(Dispatchers.Main) {
                    position = mp.currentPosition
                    duration = mp.duration
                }
            }

            longArrayOf(position, duration)
        } catch (e: Exception) {
            AppLog.e(TAG, "@@getProgress error", e)
            longArrayOf(-1, -1)
        }
    }

    // === NEW FEATURE: SINGLE & MULTI AUDIO HANDLING ===

    fun setCallback(callback: ExoplayerCallback) {
        this.callback = callback
        AppLog.d(TAG, "✅ setCallback: callback assigned = ${callback.javaClass.simpleName}")
    }

    fun initTimingUtil(timingUtil: TimingUtil) {
        this.timingUtil = timingUtil
        AppLog.d(TAG, "initTimingUtil() - TimingUtil assigned")
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

    private fun createMediaSource(url: String): MediaSource {
        val uri = url.toUri()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Connections.httpUserAgent)

        // Semua URL akan menggunakan ProgressiveMediaSource
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))
    }


    fun setAudioBarVisible(visible: Boolean) {
        isAudioBarVisible = visible
    }

    // === PLAYBACK SPEED SUPPORT ===
    fun setPlaybackSpeed(speed: Float) {
        mp.playbackParameters = mp.playbackParameters.withSpeed(speed)
    }

    fun getPlaybackSpeed(): Float = mp.playbackParameters.speed

    // == HIGHLIGHT VERSE ==
    fun highlightVerses(selectedVerses: IntArrayList, color: Int) {
        AppLog.d(TAG, "highlightVerses called - selectedVerses: $selectedVerses color: $color")
        for (verse in selectedVerses.toIntArray()) {
            callback?.onHighlightVerse(verse, color)
        }
    }

    /*private fun updateVerseHighlightUI(verseNumber: Int, color: Int) {
        AppLog.d(TAG, "Highlighting verse $verseNumber with color $color")

        activityRef?.get()?.let { activity ->
            listOf(R.id.lsSplitView0, R.id.lsSplitView1).forEach { viewId ->
                (activity.findViewById<EmptyableRecyclerView>(viewId)?.adapter as? VersesAdapter)
                    ?.updateHighlight(verseNumber, color)
            }
        }
    }*/

    // == PLAY FROM SPESIFIC VERSE ==

    fun playFromVerse(selectedVerse: Int, timingList:List<MTiming>, withEnd: Boolean = false) {
        val verseTiming = timingList.find { it.verseNumber == selectedVerse }
        if (verseTiming == null) {
            Log.e(TAG, "playFromVerse - Selected verse not found in timing list")
            return
        }

        val (startTime, endTime) = verseTiming
        val duration = endTime - startTime

        Log.d(TAG, "playFromVerse - startTime: $startTime, endTime: $endTime, selectedVerse: $selectedVerse")

        currentPlayJob?.cancel()

        currentPlayJob = scope.launch {
            seekTo(startTime)
            playOrPause(false)

            if (withEnd) {
                delay(duration)
                callback?.onAudioEnded()
                Log.d(TAG, "Triggering onAudioEnded() manually after delay")
            }
        }
    }

    fun seekTo(position: Long) {
        mp.seekTo(position)
        Log.d(TAG, "seekTo: $position")
    }

    fun playSegment(startTime: Long, endTime: Long, onComplete: () -> Unit) {
        currentPlayJob?.cancel()
        currentPlayJob = scope.launch {
            try {
                AppLog.d(TAG, "[$controllerId] playSegment start=$startTime end=$endTime state=$state isPlaying=${mp.isPlaying}")

                if (state == State.reset || state == State.error || !mp.isPlaying) {
                    AppLog.d(TAG, "playSegment: state=$state → prepare player sebelum seekTo")
                    val url = url
                    if (url != null) {
                        mediaPlayerPrepare(url, false)
                    } else {
                        AppLog.e(TAG, "URL null saat prepare untuk segment")
                        return@launch
                    }
                }

                seekTo(startTime)
                playOrPause(false)
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
        fun onHighlightVerse(verseNumber: Int, color: Int)
    }

    // === END NEW FEATURE ===
}
