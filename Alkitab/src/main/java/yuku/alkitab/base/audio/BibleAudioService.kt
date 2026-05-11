package yuku.alkitab.base.audio

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.ByteArrayOutputStream
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
import yuku.alkitab.base.S
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
 * Foreground transitions: media3's [MediaSessionService.onStartCommand] only
 * promotes to foreground when handed a media-action intent; our hybrid
 * local-binder pattern starts the service with a plain `Intent` from
 * [yuku.alkitab.base.audio.AudioBarController.ensureBound], so we override
 * [onStartCommand] to post a placeholder notification (satisfying the
 * 5-second `startForeground` deadline) and then explicitly invoke
 * [onUpdateNotification] to let media3's [DefaultMediaNotificationProvider]
 * replace the placeholder with the real MediaStyle. The channel id matches
 * the one created in `App.staticInit()`.
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
        private const val ARTWORK_SIZE_PX = 256

        /**
         * Notification id matching media3's
         * [androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID]
         * (1001) so media3's later `startForeground` *replaces* our
         * placeholder rather than stacking a second notification.
         */
        private const val PLACEHOLDER_NOTIFICATION_ID = 1001
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

    /**
     * The most recent [AudioRequest] passed to [loadChapter]. Used by
     * [skipChapter] (lock-screen / Bluetooth-driven prev/next) to compute the
     * neighbor chapter without needing the activity to be alive.
     */
    private var currentRequest: AudioRequest? = null

    /**
     * Lazily-decoded app-icon bytes used as the lock-screen / notification
     * artwork. Decoded from the per-flavor `R.mipmap.ic_launcher` (which can be
     * an adaptive XML on API 26+, hence going through [ResourcesCompat] +
     * [Drawable.draw] instead of [android.graphics.BitmapFactory.decodeResource]).
     */
    private val appIconArtworkBytes: ByteArray? by lazy { decodeAppIconArtwork() }

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : BibleAudioPlayer.Listener {
        override fun onBuffering() {
            // Re-buffering after a seek (or initial buffer) — re-arm the
            // preparing flag so the bar's progress ring + the toolbar spinner
            // come back. We deliberately reuse `preparing` rather than adding
            // a separate `buffering` field: from the user's POV, both states
            // are "we asked to play but no audio is coming out yet", which is
            // what the spinner communicates.
            _playbackState.update { it.copy(preparing = true) }
        }

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

        // Wrap the inner ExoPlayer so the system's transport controls (lock-
        // screen, Bluetooth, Android Auto) skip *chapters* instead of media
        // items. See [BibleChapterNavigatingPlayer] for the rationale; the
        // short version is "we only ever have one MediaItem queued, so the
        // standard skipNext/skipPrevious would otherwise be no-ops."
        val mediaSessionPlayer = BibleChapterNavigatingPlayer(
            inner = player.exoPlayer,
            onSeekToNextChapter = { skipChapter(1) },
            onSeekToPrevChapter = { skipChapter(-1) },
        )

        mediaSession = MediaSession.Builder(this, mediaSessionPlayer)
            .setSessionActivity(sessionActivity)
            .build()
            .also { addSession(it) }

        // Use our pre-created low-importance channel so the notification doesn't
        // make sound or vibrate.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.audio_bible_notification_channel_name)
                .build()
        )

        // Always post a notification, even while the player is idle (i.e.
        // between `onCreate` and the first `loadChapter`). The activity calls
        // `startForegroundService` on us in [AudioBarController.ensureBound]
        // and the system requires `startForeground` within ~5 seconds; if we
        // wait for the player to leave IDLE we risk a
        // `ForegroundServiceDidNotStartInTimeException` on slow networks.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Post a placeholder notification *immediately* so we satisfy the
        // 5-second startForeground deadline that begins when
        // [AudioBarController.ensureBound] calls `startForegroundService`. The
        // base [MediaSessionService.onStartCommand] only posts a notification
        // when the incoming intent is a known media action (`ACTION_PLAY`,
        // `ACTION_MEDIA_BUTTON`, etc.) — our hybrid local-binder pattern
        // starts the service with a plain `Intent`, so we have to bridge the
        // gap ourselves.
        promoteToForegroundWithPlaceholder()
        val result = super.onStartCommand(intent, flags, startId)
        // Then ask media3's notification manager to replace the placeholder
        // with the real MediaStyle (title, artist, artwork, skip buttons).
        // Without this nudge, the manager's MediaController-driven update
        // path can lag behind the user-visible "I just tapped play" moment
        // because it depends on the controller asynchronously connecting.
        mediaSession?.let { session -> onUpdateNotification(session, /* startInForegroundRequired = */ true) }
        return result
    }

    /**
     * Posts a minimal "Loading audio…" notification on the audio_bible channel
     * and calls `startForeground`, satisfying the 5-second startForeground
     * deadline. Subsequent calls update the same notification id, which
     * media3's `MediaNotificationManager` then takes over with the full
     * MediaStyle controls (title/artist/artwork/skip buttons) — the
     * placeholder is visible for at most a few hundred ms in normal use.
     */
    private fun promoteToForegroundWithPlaceholder() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_audio)
            .setContentTitle(getString(R.string.audio_bible_notification_loading_title))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .build()
        ServiceCompat.startForeground(
            this,
            PLACEHOLDER_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
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
        currentRequest = request
        // New chapter — reset highlight from any previous chapter and clear errors.
        highlightTracker.setTiming(emptyList())
        _playbackState.update {
            it.copy(
                preparing = true,
                isPlaying = false,
                bookId = request.bookId,
                chapter_1 = request.chapter_1,
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

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(request.displayTitle)
                .setArtist(request.displaySubtitle)
            // The artwork is the same on every chapter for v1 (see PRD §4.4).
            // We attach the bytes inline rather than a Uri because the default
            // BitmapLoader only resolves http/file/content schemes, not the
            // android.resource://… we'd otherwise need for the launcher icon.
            appIconArtworkBytes?.let { bytes ->
                metadataBuilder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(metadataBuilder.build())
                .build()
            player.exoPlayer.setMediaItem(mediaItem)
            player.exoPlayer.playWhenReady = true
            player.exoPlayer.prepare()
        }

        // Stay on Dispatchers.Main.immediate (the scope's default) so
        // setTiming runs on the same thread as positionJob's update calls —
        // HighlightTracker is not thread-safe by design. The repository
        // already does its own withContext(Dispatchers.IO) for the network
        // hop, so the blocking work is still off the main thread.
        timingJob = scope.launch {
            val timing = BibleAudioRepository.fetchTiming(
                request.versionId,
                request.bookId,
                request.chapter_1,
            )
            highlightTracker.setTiming(timing?.verses ?: emptyList())
        }
    }

    fun play() {
        println("YUKU0 svc.state play")
        player.play()
        _playbackState.update { it.copy(isPlaying = true) }
        startPositionPolling()
    }

    fun pause() {
        println("YUKU0 svc.state pause")
        player.pause()
        positionJob?.cancel()
        _playbackState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        highlightTracker.update(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    /**
     * Skip to the start of the next verse based on timing data. No-op when
     * timing isn't loaded, when we're already past the last verse, or when
     * the player isn't in a seekable state.
     */
    fun seekToNextVerse() {
        val target = highlightTracker.getNextVerseStartMs(player.currentPositionMs) ?: return
        seekTo(target)
    }

    /**
     * Skip to the previous verse using music-player semantics: a quick tap
     * (within the first ~2 s of a verse) jumps to the prior verse; otherwise
     * it restarts the current verse. See [HighlightTracker.getPrevVerseStartMs].
     */
    fun seekToPrevVerse() {
        val target = highlightTracker.getPrevVerseStartMs(player.currentPositionMs) ?: return
        seekTo(target)
    }

    /**
     * Returns the `verse_1` that [positionMs] falls inside, or `0` if none.
     * Pure read — used by the slider's live drag preview so the verse
     * highlight in the reader can follow the thumb without committing a seek.
     */
    fun peekVerseAt(positionMs: Long): Int = highlightTracker.peekVerseAt(positionMs)

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
        println("YUKU0 svc.state stop")
        loadJob?.cancel()
        timingJob?.cancel()
        positionJob?.cancel()
        currentRequest = null
        player.pause()
        _playbackState.value = PlaybackState.IDLE
        stopSelf()
    }

    /**
     * Loads the chapter immediately before ([direction] = -1) or after
     * ([direction] = 1) the currently-loaded one. No-op when nothing has been
     * loaded, when [direction] is invalid, or at the Bible boundary.
     *
     * Used both by:
     *  - the in-app audio bar's prev/next-chapter buttons (via
     *    [yuku.alkitab.base.audio.AudioBarController]), and
     *  - the system transport controls (lock-screen / Bluetooth headset),
     *    routed through [BibleChapterNavigatingPlayer].
     *
     * The activity does not need to be alive for this to work — the
     * [yuku.alkitab.model.Version] is resolved through [S]. When the activity
     * *is* alive, `AudioBarController` observes the resulting state change and
     * navigates `IsiActivity` to keep both surfaces in sync.
     */
    fun skipChapter(direction: Int) {
        val current = currentRequest ?: return
        val resolvedVersion = S.getVersionFromVersionId(current.versionId)?.version
        val version = resolvedVersion ?: S.activeVersion()
        val (book, chapter1) = BibleNeighborResolver.neighbor(
            version,
            current.bookId,
            current.chapter_1,
            direction,
        ) ?: return

        // Reuse the most recent displaySubtitle when we have no resolved
        // Version to query — keeps notification metadata stable rather than
        // flipping to a different version's short name across chapter skips.
        val versionShortName = resolvedVersion?.shortName ?: current.displaySubtitle
        loadChapter(
            AudioRequest(
                versionId = current.versionId,
                bookId = book.bookId,
                chapter_1 = chapter1,
                displayTitle = "${book.shortName} $chapter1",
                displaySubtitle = versionShortName,
            )
        )
    }

    /**
     * Decodes the launcher icon to PNG bytes for use as MediaSession artwork.
     * Goes through [ResourcesCompat.getDrawable] + draw-into-bitmap so that
     * adaptive-icon XML (`mipmap-anydpi-v26/ic_launcher.xml`, used by every
     * production flavor) is handled correctly — `BitmapFactory.decodeResource`
     * returns null for those.
     *
     * Sized at 256×256 px: bigger than the typical lock-screen large-icon
     * slot (192 dp ≈ 384 px on xxhdpi, but the system downscales fine) and
     * still small enough that the encoded PNG comes in under ~50 KB, which
     * keeps the [MediaMetadata] cheap to ship across IPC.
     */
    private fun decodeAppIconArtwork(): ByteArray? {
        return try {
            val drawable = ResourcesCompat.getDrawable(resources, R.mipmap.ic_launcher, theme)
                ?: return null
            val size = ARTWORK_SIZE_PX
            val bitmap = createBitmap(size, size)
            try {
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                ByteArrayOutputStream().use { baos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    baos.toByteArray()
                }
            } finally {
                // Always recycle, even if compress() throws — long-running
                // services accumulate native bitmap memory otherwise.
                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Failing to decode artwork should NOT break audio — fall back to
            // a metadata-only notification (title + artist).
            AppLog.w(TAG, "Failed to decode launcher icon artwork: ${e.message}")
            null
        }
    }

    private var pollTickCount = 0
    private fun startPositionPolling() {
        println("YUKU0 svc.poll START")
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
                pollTickCount++
                if (pollTickCount % 10 == 0) {
                    println("YUKU0 svc.poll tick x10 pos=$pos isPlaying=${player.isPlaying} total=$pollTickCount")
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }
}
