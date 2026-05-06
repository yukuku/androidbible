package yuku.alkitab.base.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import yuku.alkitab.base.IsiActivity
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.R

/**
 * Foreground [MediaSessionService] that owns a [BibleAudioPlayer] and exposes
 * playback state to the M3 Compose UI via a local binder + [StateFlow].
 *
 * Two binding paths share the same service instance:
 *  - **MediaSession (system)** — `super.onBind(intent)` returns the standard
 *    media3 stub so the OS can drive playback through MediaController, lock
 *    screen, Bluetooth, Android Auto, etc.
 *  - **Local (in-app)** — clients send `Intent` with [ACTION_LOCAL_BIND] to
 *    receive a [LocalBinder]; they can then read [playbackState] and call
 *    [loadChapter] / [play] / [pause] / [seekTo] / [setSpeed] / [stop]
 *    directly. Used by the M3 `AudioBarController`.
 *
 * Foreground transitions (notification post / removal) are handled by media3
 * automatically based on `Player.isPlaying`. The notification's MediaStyle,
 * channel, and metadata come from [DefaultMediaNotificationProvider]; the
 * channel id we set here matches the one created in `App.staticInit()`.
 *
 * Audio focus, becoming-noisy, lock-screen / Bluetooth media-button handling
 * all come for free with `MediaSession` + the audio attributes set on the
 * [BibleAudioPlayer]'s ExoPlayer.
 */
@OptIn(UnstableApi::class)
class BibleAudioService : MediaSessionService() {

    companion object {
        /** Action on the binding [Intent] that asks for the in-app [LocalBinder]. */
        const val ACTION_LOCAL_BIND = "yuku.alkitab.audio.ACTION_LOCAL_BIND"

        /**
         * Notification channel id; must match the channel created at app startup
         * (`App.staticInit()`). media3 will auto-create the channel here too if
         * we forget, but we want IMPORTANCE_LOW + no sound, which means we have
         * to be the ones to create it first — channel attributes are immutable
         * after creation.
         */
        const val NOTIFICATION_CHANNEL_ID = "audio_bible"

        private const val POSITION_POLL_INTERVAL_MS = 100L
        private const val TAG = "BibleAudioService"
    }

    /** Parameters for [loadChapter]. The display fields drive the lock-screen metadata. */
    data class AudioRequest(
        val versionId: String,
        val bookId: Int,
        val chapter_1: Int,
        val displayTitle: String,
        val displaySubtitle: String,
    )

    inner class LocalBinder : Binder() {
        val service: BibleAudioService get() = this@BibleAudioService
    }

    private val localBinder = LocalBinder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null
    private var loadJob: Job? = null
    private var timingJob: Job? = null

    private lateinit var player: BibleAudioPlayer
    private var mediaSession: MediaSession? = null
    private val highlightTracker = HighlightTracker()

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : BibleAudioPlayer.Listener {
        override fun onReady() {
            startPositionPolling()
            _playbackState.update {
                it.copy(
                    preparing = false,
                    isPlaying = player.isPlaying,
                    durationMs = player.durationMs,
                    speed = player.speed,
                    error = null,
                )
            }
        }

        override fun onEnded() {
            // M2: stop polling and report stopped. Auto-advance to the next
            // chapter is M5 polish and lives outside the service.
            positionJob?.cancel()
            _playbackState.update {
                it.copy(isPlaying = false, positionMs = player.durationMs)
            }
        }

        override fun onError(error: PlaybackException) {
            AppLog.w(TAG, "player error: ${error.errorCodeName} ${error.message}")
            positionJob?.cancel()
            _playbackState.update {
                it.copy(
                    preparing = false,
                    isPlaying = false,
                    error = error.errorCodeName,
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = BibleAudioPlayer(applicationContext)
        player.setListener(playerListener)

        // Tapping the notification opens the reader.
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, IsiActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player.exoPlayer)
            .setSessionActivity(sessionActivity)
            .build()

        // Use our pre-created low-importance channel so the notification doesn't
        // make sound or vibrate.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.audio_bible_notification_channel_name)
                .build()
        )

        // Mirror highlight changes into the playback state.
        scope.launch {
            highlightTracker.verse1.collect { v ->
                _playbackState.update { it.copy(verse_1 = v) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == ACTION_LOCAL_BIND) {
            return localBinder
        }
        return super.onBind(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Match Spotify / YouTube Music: swiping the app from recents while
        // audio is playing should keep playback alive. If we're paused,
        // releasing is a reasonable cleanup.
        if (player.isPlaying) {
            return
        }
        stopSelf()
    }

    override fun onDestroy() {
        positionJob?.cancel()
        loadJob?.cancel()
        timingJob?.cancel()
        scope.cancel()
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    // -- public API surfaced via [LocalBinder] -------------------------------

    /**
     * Starts loading [request]'s chapter. Cancels any in-flight load. Updates
     * [playbackState] to `preparing = true` immediately; the player will fire
     * its `onReady` listener once buffering is done, at which point `preparing`
     * flips to false. Timing data is fetched in parallel with audio buffering.
     */
    fun loadChapter(request: AudioRequest) {
        loadJob?.cancel()
        timingJob?.cancel()
        // New chapter — reset highlight from any previous chapter and clear errors.
        highlightTracker.setTiming(emptyList())
        _playbackState.update {
            it.copy(
                preparing = true,
                isPlaying = false,
                verse_1 = 0,
                positionMs = 0L,
                durationMs = 0L,
                error = null,
            )
        }

        loadJob = scope.launch {
            val url = BibleAudioRepository.buildChapterUrl(
                request.versionId,
                request.bookId,
                request.chapter_1,
            )
            if (url == null) {
                _playbackState.update {
                    it.copy(preparing = false, error = "no_audio_for_version")
                }
                return@launch
            }

            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(request.displayTitle)
                        .setArtist(request.displaySubtitle)
                        .build()
                )
                .build()
            player.exoPlayer.setMediaItem(mediaItem)
            player.exoPlayer.playWhenReady = true
            player.exoPlayer.prepare()
        }

        timingJob = scope.launch(Dispatchers.IO) {
            val timing = BibleAudioRepository.fetchTiming(
                request.versionId,
                request.bookId,
                request.chapter_1,
            )
            highlightTracker.setTiming(timing?.verses ?: emptyList())
        }
    }

    fun play() {
        player.play()
        _playbackState.update { it.copy(isPlaying = true) }
        startPositionPolling()
    }

    fun pause() {
        player.pause()
        positionJob?.cancel()
        _playbackState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        highlightTracker.update(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    fun setSpeed(speed: Float) {
        player.setSpeed(speed)
        _playbackState.update { it.copy(speed = speed) }
    }

    /**
     * Stops playback and tears the service down (clears the foreground
     * notification). Safe to call after `loadChapter` even if the player never
     * reached READY.
     */
    fun stop() {
        loadJob?.cancel()
        timingJob?.cancel()
        positionJob?.cancel()
        player.pause()
        _playbackState.value = PlaybackState.IDLE
        stopSelf()
    }

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val pos = player.currentPositionMs
                highlightTracker.update(pos)
                _playbackState.update {
                    it.copy(
                        isPlaying = player.isPlaying,
                        positionMs = pos,
                        durationMs = player.durationMs,
                    )
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }
}
