package yuku.alkitab.songs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.decoder.midi.MidiExtractor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import yuku.alkitab.base.audio.AUDIO_LOG_LIMIT
import yuku.alkitab.base.audio.AudioHttpEventLogger
import yuku.alkitab.base.audio.AudioLogMessage
import yuku.alkitab.base.audio.AudioPlayback
import yuku.alkitab.base.audio.AudioService
import yuku.alkitab.base.audio.stripErrorCodePrefix
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.R

/** Song playback state and controls owned by [AudioService]. */
@OptIn(UnstableApi::class)
class SongAudioPlayback(context: Context, owner: AudioService) : AudioPlayback(context, owner) {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "audio_song"
        private const val TAG = "SongAudioPlayback"
    }

    data class SongRequest(
        val url: String,
        val displayTitle: String,
        val displaySubtitle: String,
        val loop: Boolean,
    )

    private lateinit var player: ExoPlayer

    @Volatile
    private var loadGeneration = 0

    private val _playbackState = MutableStateFlow(SongPlaybackState.IDLE)
    val playbackState: StateFlow<SongPlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!isActive) return
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    appendLog(AudioLogMessage(R.string.audio_log_player_buffering))
                    _playbackState.update { it.copy(preparing = true, error = null) }
                    updateDiagnosticTimer()
                }
                Player.STATE_READY -> {
                    appendLog(AudioLogMessage(R.string.audio_log_player_ready))
                    _playbackState.update {
                        it.copy(preparing = false, ended = false, isPlaying = player.isPlaying, error = null)
                    }
                    updateDiagnosticTimer()
                }
                Player.STATE_ENDED -> {
                    _playbackState.update { it.copy(preparing = false, isPlaying = false, ended = true) }
                    updateDiagnosticTimer()
                }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isActive) return
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            updateDiagnosticTimer()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!isActive) return
            updateDiagnosticTimer()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!isActive) return
            AppLog.w(TAG, "player error: ${error.errorCodeName} ${error.message}")
            val code = stripErrorCodePrefix(error.errorCodeName)
            val detail = error.message
            appendLog(
                if (detail != null) AudioLogMessage(R.string.audio_log_player_error_detail, listOf(code, detail))
                else AudioLogMessage(R.string.audio_log_player_error, listOf(code))
            )
            _playbackState.update { it.copy(preparing = false, isPlaying = false, error = code) }
            updateDiagnosticTimer()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = buildPlayer()
        player.addListener(playerListener)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            SongViewActivity.createIntent().addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        createMediaSession(
            player, sessionActivity,
            NOTIFICATION_CHANNEL_ID, R.string.audio_song_notification_channel_name,
        )
    }

    private fun buildPlayer(): ExoPlayer {
        val loggingClient = Connections.okHttp.newBuilder()
            .eventListenerFactory {
                // HTTP cancellation callbacks can arrive after another load has started.
                val generation = loadGeneration
                AudioHttpEventLogger { message ->
                    scope.launch {
                        if (isActive && generation == loadGeneration) appendLog(message)
                    }
                }
            }
            .build()
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(loggingClient)
            .setUserAgent(Connections.httpUserAgent)
        val extractorsFactory = ExtractorsFactory {
            arrayOf<Extractor>(Mp3Extractor(), MidiExtractor())
        }
        // DefaultRenderersFactory + EXTENSION_RENDERER_MODE_ON activates the
        // reflectively-loaded MidiRenderer (JSyn) shipped by media3-exoplayer-midi.
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(
            this,
            renderersFactory,
            ProgressiveMediaSource.Factory(okHttpDataSourceFactory, extractorsFactory),
        )
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    override val isPlaybackOngoing: Boolean
        get() = ::player.isInitialized && player.playWhenReady &&
            (player.playbackState == androidx.media3.common.Player.STATE_READY ||
                player.playbackState == androidx.media3.common.Player.STATE_BUFFERING)
    override fun releasePlayer() {
        if (::player.isInitialized) player.release()
    }

    override fun onDestroy() {
        _playbackState.value = SongPlaybackState.IDLE
        super.onDestroy()
    }

    fun load(request: SongRequest) {
        owner.activate(this)
        cancelDiagnosticTimer()
        loadGeneration++
        _playbackState.value = SongPlaybackState.IDLE.copy(
            hasMedia = true,
            preparing = true,
            isDiagnosticVisible = _playbackState.value.isDiagnosticVisible,
        )
        appendLog(AudioLogMessage(R.string.audio_log_loading, listOf(request.displayTitle)))
        val metadata = MediaMetadata.Builder()
            .setTitle(request.displayTitle)
            .setArtist(request.displaySubtitle)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(request.url)
            .setMediaMetadata(metadata)
            .build()
        player.repeatMode = if (request.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaItem(mediaItem)
        player.playWhenReady = true
        player.prepare()
        updateDiagnosticTimer()
    }

    fun play() {
        if (!isActive) return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
        }
        player.playWhenReady = true
        updateDiagnosticTimer()
    }

    fun pause() {
        if (!isActive) return
        player.playWhenReady = false
        updateDiagnosticTimer()
    }

    fun seekTo(positionMs: Long) {
        if (!isActive) return
        val duration = durationMs
        if (duration > 0) {
            player.seekTo(positionMs.coerceIn(0L, duration))
            updateDiagnosticTimer()
        }
    }

    fun setLoop(loop: Boolean) {
        if (!isActive) return
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    val currentPositionMs: Long
        get() = if (isActive) player.currentPosition.coerceAtLeast(0L) else -1L

    val durationMs: Long
        get() = if (isActive) player.duration.let { if (it < 0L) -1L else it } else -1L

    override fun resetPlayback() {
        cancelDiagnosticTimer()
        loadGeneration++
        _playbackState.value = SongPlaybackState.IDLE
        player.stop()
        player.clearMediaItems()
    }

    private fun updateDiagnosticTimer() {
        val state = _playbackState.value
        if (state.isDiagnosticVisible || !state.hasMedia || player.isPlaying || player.playbackState == Player.STATE_ENDED || !player.playWhenReady) {
            cancelDiagnosticTimer()
            return
        }
        scheduleDiagnostic(
            isWaiting = { player.playWhenReady && !player.isPlaying && player.playbackState != Player.STATE_ENDED },
            onTimeout = { _playbackState.update { it.copy(isDiagnosticVisible = true) } },
        )
    }

    private fun appendLog(message: AudioLogMessage) {
        val entry = logEntry(message)
        _playbackState.update { it.copy(logs = (it.logs + entry).takeLast(AUDIO_LOG_LIMIT)) }
    }
}
