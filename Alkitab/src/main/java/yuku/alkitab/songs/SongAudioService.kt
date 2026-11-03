package yuku.alkitab.songs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
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
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import yuku.alkitab.base.audio.AudioHttpEventLogger
import yuku.alkitab.base.audio.AudioLogEntry
import yuku.alkitab.base.audio.AudioLogMessage
import yuku.alkitab.base.audio.AudioPlaybackCoordinator
import yuku.alkitab.base.audio.stripErrorCodePrefix
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.widget.ConfigurationWrapper
import yuku.alkitab.debug.R

/**
 * Foreground [MediaSessionService] for kidung (hymn) audio. Owns a single media3
 * [ExoPlayer] that decodes both MP3 (over OkHttp) and MIDI (via the experimental
 * `media3-exoplayer-midi` JSyn synth), so one code path covers both formats and
 * both get a lock-screen / background media notification.
 *
 * Registers with [AudioPlaybackCoordinator] so starting a hymn stops Bible audio
 * and vice-versa. Mirrors [yuku.alkitab.base.audio.BibleAudioService]'s
 * lifecycle: clients `startService` then `bindService` with [ACTION_LOCAL_BIND];
 * media3 promotes us to foreground once the player becomes user-engaged.
 */
@OptIn(UnstableApi::class)
class SongAudioService : MediaSessionService(), AudioPlaybackCoordinator.Session {

    companion object {
        const val ACTION_LOCAL_BIND = "yuku.alkitab.songs.ACTION_LOCAL_BIND"
        const val NOTIFICATION_CHANNEL_ID = "audio_song"
        private const val MEDIA_SESSION_ID = "song_audio"
        private const val TAG = "SongAudioService"
        private const val DIAGNOSTIC_DELAY_MS = 5_000L
        private const val MAX_LOG_ENTRIES = 500
    }

    data class SongRequest(
        val url: String,
        val displayTitle: String,
        val displaySubtitle: String,
        val loop: Boolean,
    )

    /**
     * Local IPC binder for the in-process [SongAudioController]. Static + a
     * [WeakReference] so the Binder framework's JNI globals can't pin the whole
     * service alive after `unbindService`; see the equivalent rationale on
     * [yuku.alkitab.base.audio.BibleAudioService.LocalBinder].
     */
    class LocalBinder internal constructor(service: SongAudioService) : Binder() {
        private val ref = WeakReference(service)
        val service: SongAudioService? get() = ref.get()
    }

    private val localBinder = LocalBinder(this)

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var diagnosticJob: Job? = null
    @Volatile
    private var loadGeneration = 0
    private var localizedContext: Context? = null
    private var localizedContextSerial = -1

    private val _playbackState = MutableStateFlow(SongPlaybackState.IDLE)
    val playbackState: StateFlow<SongPlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
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
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            updateDiagnosticTimer()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateDiagnosticTimer()
        }

        override fun onPlayerError(error: PlaybackException) {
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

        mediaSession = MediaSession.Builder(this, player)
            .setId(MEDIA_SESSION_ID)
            .setSessionActivity(sessionActivity)
            .build()
            .also { addSession(it) }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.audio_song_notification_channel_name)
                .build()
        )
    }

    private fun buildPlayer(): ExoPlayer {
        val loggingClient = Connections.okHttp.newBuilder()
            .eventListenerFactory {
                // HTTP cancellation callbacks can arrive after another load has started.
                val generation = loadGeneration
                AudioHttpEventLogger { message ->
                    scope.launch {
                        if (generation == loadGeneration) appendLog(message)
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

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == ACTION_LOCAL_BIND) {
            return localBinder
        }
        return super.onBind(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.isPlaying) {
            return
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        _playbackState.value = SongPlaybackState.IDLE
        mediaSession?.release()
        mediaSession = null
        player.release()
        AudioPlaybackCoordinator.release(this)
        super.onDestroy()
    }

    override fun stopPlayback() {
        stop()
    }

    // -- public API surfaced via LocalBinder ---------------------------------

    fun load(request: SongRequest) {
        AudioPlaybackCoordinator.acquire(this)
        diagnosticJob?.cancel()
        diagnosticJob = null
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
        AudioPlaybackCoordinator.acquire(this)
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
        }
        player.playWhenReady = true
        updateDiagnosticTimer()
    }

    fun pause() {
        player.playWhenReady = false
        updateDiagnosticTimer()
    }

    fun seekTo(positionMs: Long) {
        val duration = durationMs
        if (duration > 0) {
            player.seekTo(positionMs.coerceIn(0L, duration))
            updateDiagnosticTimer()
        }
    }

    fun setLoop(loop: Boolean) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    val currentPositionMs: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    val durationMs: Long
        get() = player.duration.let { if (it < 0L) -1L else it }

    fun stop() {
        diagnosticJob?.cancel()
        diagnosticJob = null
        loadGeneration++
        _playbackState.value = SongPlaybackState.IDLE
        player.stop()
        player.clearMediaItems()
        stopSelf()
        AudioPlaybackCoordinator.release(this)
    }

    private fun updateDiagnosticTimer() {
        val state = _playbackState.value
        if (state.isDiagnosticVisible || !state.hasMedia || player.isPlaying || player.playbackState == Player.STATE_ENDED || !player.playWhenReady) {
            diagnosticJob?.cancel()
            diagnosticJob = null
            return
        }
        if (diagnosticJob?.isActive == true) return

        diagnosticJob = scope.launch {
            delay(DIAGNOSTIC_DELAY_MS)
            if (player.playWhenReady && !player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                _playbackState.update { it.copy(isDiagnosticVisible = true) }
            }
        }
    }

    private fun appendLog(message: AudioLogMessage) {
        val serial = ConfigurationWrapper.getSerialCounter()
        if (serial != localizedContextSerial || localizedContext == null) {
            localizedContext = ConfigurationWrapper.localizedContext(this)
            localizedContextSerial = serial
        }
        val entry = AudioLogEntry(System.currentTimeMillis(), message.resolve(localizedContext ?: this))
        _playbackState.update { it.copy(logs = (it.logs + entry).takeLast(MAX_LOG_ENTRIES)) }
    }
}
