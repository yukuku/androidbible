package yuku.alkitab.base.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.IsiActivity
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.R

/** Bible playback state and controls owned by [AudioService]. */
@OptIn(UnstableApi::class)
class BibleAudioPlayback(context: Context, owner: AudioService) : AudioPlayback(context, owner) {

    companion object {
        /**
         * Reuse the notification channel created in App.staticInit() so playback
         * notifications stay silent and low priority.
         */
        const val NOTIFICATION_CHANNEL_ID = "audio_bible"

        private const val POSITION_POLL_INTERVAL_MS = 100L
        private const val ARTWORK_SIZE_PX = 256

        /**
         * The log after recording [entry]. Returns [logs] untouched when
         * [eventGeneration] is not [currentGeneration], which is how a
         * superseded chapter load's trailing cancellation events are kept out
         * of the new chapter's log. Otherwise appends, keeping at most
         * [maxEntries] newest. Pure for unit testing.
         */
        internal fun appendLogEntry(
            logs: List<AudioLogEntry>,
            entry: AudioLogEntry,
            eventGeneration: Int,
            currentGeneration: Int,
            maxEntries: Int = AUDIO_LOG_LIMIT,
        ): List<AudioLogEntry> {
            if (eventGeneration != currentGeneration) return logs
            return (logs + entry).takeLast(maxEntries)
        }

        private const val DEFAULT_PLAYBACK_SPEED = 1.0f

        private const val TAG = "BibleAudioPlayback"

        /**
         * Process-wide flag (the service is local and single-instance) that lets
         * [AudioBarController] decide whether to re-show the audio bar after the
         * activity is recreated or returns from the background *without* binding.
         * Binding would spin the service up via `BIND_AUTO_CREATE` even for users
         * who never started audio. Set true while a chapter is loaded, cleared on
         * stop / destroy.
         */
        @Volatile
        var hasActiveSession: Boolean = false
            private set
    }

    /** Parameters for [loadChapter]. The display fields drive the lock-screen metadata. */
    data class AudioRequest(
        val versionId: String,
        val audioId: String,
        val bookId: Int,
        val chapter_1: Int,
        val displayTitle: String,
        val displaySubtitle: String,
        /**
         * 1-based verse to start playback at, or `0` to start at the beginning of
         * the chapter. Honored only once timing is loaded; falls back to position
         * 0 when the chapter has no timing or no entry for this verse.
         */
        val startVerse_1: Int,
    )

    private var positionJob: Job? = null
    private var loadJob: Job? = null
    private var timingJob: Job? = null

    private lateinit var player: BibleAudioPlayer
    private val highlightTracker = HighlightTracker()

    /**
     * The most recent [AudioRequest] passed to [loadChapter]. Used by
     * [skipChapter] (lock-screen / Bluetooth-driven prev/next) to compute the
     * neighbor chapter without needing the activity to be alive.
     */
    private var currentRequest: AudioRequest? = null

    /**
     * Deferred initial-seek state for [AudioRequest.startVerse_1]. The seek can
     * only run once BOTH the player is READY ([playerReadyForSeek]) and timing
     * has resolved ([timingLoaded]); the two arrive asynchronously and in either
     * order. [tryInitialSeek] is called from both completion points and fires
     * (or gives up, falling back to position 0) when both are ready.
     */
    private var pendingStartVerse1 = 0
    private var playerReadyForSeek = false
    private var timingLoaded = false

    private val appIconArtworkBytes: ByteArray? by lazy { decodeAppIconArtwork() }

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var persistedSpeed: Float = DEFAULT_PLAYBACK_SPEED

    /**
     * Which chapter load the log is currently accumulating. Bumped on every
     * [loadChapter] and on [stop]; log events stamped with an older value are
     * dropped. See [BibleAudioPlayer.logGeneration].
     */
    private var loadGeneration = 0

    private val playerListener = object : BibleAudioPlayer.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isActive) return
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startPositionPolling()
            } else {
                positionJob?.cancel()
            }
            updateDiagnosticTimer()
        }

        override fun onPlayWhenReadyChanged() {
            if (!isActive) return
            updateDiagnosticTimer()
        }

        override fun onBuffering() {
            if (!isActive) return
            // `preparing` doubles as the buffering flag rather than having a
            // separate field: to the user both states are "we asked to play but
            // no audio is coming out yet", which is what the spinner says.
            _playbackState.update { it.copy(preparing = true, isPlaying = false) }
            updateDiagnosticTimer()
        }

        override fun onReady() {
            if (!isActive) return
            playerReadyForSeek = true
            _playbackState.update {
                it.copy(
                    preparing = false,
                    isPlaying = player.isPlaying,
                    durationMs = player.durationMs,
                    speed = player.speed,
                    error = null,
                )
            }
            // A deferred seek can synchronously report buffering, so publish READY before seeking.
            tryInitialSeek()
            updateDiagnosticTimer()
        }

        override fun onEnded() {
            if (!isActive) return
            positionJob?.cancel()
            updateDiagnosticTimer()
            _playbackState.update {
                it.copy(isPlaying = false, positionMs = player.durationMs)
            }
            // Auto-advance to the next chapter. At the Bible boundary
            // skipChapter is a no-op, so we stay parked at the end of
            // Revelation 22.
            if (currentRequest != null) {
                skipChapter(1)
            }
        }

        override fun onError(error: PlaybackException) {
            if (!isActive) return
            AppLog.w(TAG, "player error: ${error.errorCodeName} ${error.message}")
            positionJob?.cancel()
            _playbackState.update {
                it.copy(
                    preparing = false,
                    isPlaying = false,
                    error = stripErrorCodePrefix(error.errorCodeName),
                )
            }
            updateDiagnosticTimer()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = BibleAudioPlayer(applicationContext) { generation, message ->
            scope.launch { appendLog(generation, message) }
        }
        player.setListener(playerListener)

        // Apply the persisted speed to the player up front so the first chapter
        // load already plays at the right speed.
        persistedSpeed = Preferences.getFloat(Prefkey.audioPlaybackSpeed, DEFAULT_PLAYBACK_SPEED)
        player.setSpeed(persistedSpeed)
        _playbackState.update { it.copy(speed = persistedSpeed) }

        // Tapping the notification opens the reader.
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, IsiActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        // Wrap the inner ExoPlayer so the system's transport controls (lock
        // screen, Bluetooth, Android Auto) skip *chapters* instead of media
        // items. Only one MediaItem is ever queued, so the standard
        // skipNext/skipPrevious would otherwise be no-ops. See
        // BibleChapterNavigatingPlayer.
        val mediaSessionPlayer = BibleChapterNavigatingPlayer(
            inner = player.exoPlayer,
            onSeekToNextChapter = { skipChapter(1) },
            onSeekToPrevChapter = { skipChapter(-1) },
        )

        createMediaSession(
            mediaSessionPlayer, sessionActivity,
            NOTIFICATION_CHANNEL_ID, R.string.audio_bible_notification_channel_name,
        )

        scope.launch {
            highlightTracker.verse1.collect { v ->
                _playbackState.update { it.copy(verse_1 = v) }
            }
        }
    }

    override val isPlaybackOngoing: Boolean
        get() = ::player.isInitialized && player.exoPlayer.playWhenReady &&
            (player.exoPlayer.playbackState == androidx.media3.common.Player.STATE_READY ||
                player.exoPlayer.playbackState == androidx.media3.common.Player.STATE_BUFFERING)
    override fun releasePlayer() {
        if (::player.isInitialized) player.release()
    }

    override fun onDestroy() {
        hasActiveSession = false
        positionJob?.cancel()
        loadJob?.cancel()
        timingJob?.cancel()
        _playbackState.value = PlaybackState.IDLE
        super.onDestroy()
    }

    /**
     * Starts loading [request]'s chapter. Cancels any in-flight load. Updates
     * [playbackState] to `preparing = true` immediately; the player will fire
     * its `onReady` listener once buffering is done, at which point `preparing`
     * flips to false. Timing data is fetched in parallel with audio buffering.
     */
    fun loadChapter(request: AudioRequest) {
        owner.activate(this)
        loadJob?.cancel()
        timingJob?.cancel()
        cancelDiagnosticTimer()
        // The player still holds the outgoing chapter until the load job below
        // swaps the media item, so a poll tick would overwrite the
        // position/duration reset below with the old chapter's values. Polling
        // resumes when the player reaches READY (or on an explicit play()).
        positionJob?.cancel()
        currentRequest = request
        hasActiveSession = true
        pendingStartVerse1 = request.startVerse_1
        playerReadyForSeek = false
        timingLoaded = false
        // The log resets here because a retry is a fresh load attempt; mixing
        // its events with the failed attempt's would make the log sheet read as
        // one non-chronological HTTP conversation. Bumping the generation
        // before the media item is swapped keeps the outgoing chapter's
        // cancellation events out of the new log, since those arrive from
        // OkHttp after this reset. See [BibleAudioPlayer.logGeneration].
        loadGeneration++
        player.logGeneration = loadGeneration
        highlightTracker.setTiming(emptyList())
        _playbackState.update {
            it.copy(
                preparing = true,
                isPlaying = false,
                versionId = request.versionId,
                audioId = request.audioId,
                bookId = request.bookId,
                chapter_1 = request.chapter_1,
                verse_1 = 0,
                positionMs = 0L,
                durationMs = 0L,
                error = null,
                logs = listOf(
                    AudioLogEntry(
                        System.currentTimeMillis(),
                        localizedContext().getString(R.string.audio_log_loading, request.displayTitle),
                    )
                ),
                isDiagnosticVisible = false,
            )
        }

        loadJob = scope.launch {
            val url = BibleAudioRepository.buildChapterUrl(
                request.versionId,
                request.audioId,
                request.bookId,
                request.chapter_1,
            )
            if (url == null) {
                appendLog(AudioLogMessage(R.string.audio_log_no_audio))
                _playbackState.update {
                    it.copy(preparing = false, error = "no_audio_for_version")
                }
                return@launch
            }

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(request.displayTitle)
                .setArtist(request.displaySubtitle)
            // Bytes are attached inline rather than as a Uri because the default
            // BitmapLoader only resolves http/file/content schemes, not the
            // android.resource://... a launcher icon would need.
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
            updateDiagnosticTimer()
        }

        // Stay on Dispatchers.Main.immediate (the scope's default) so setTiming
        // runs on the same thread as positionJob's update calls;
        // HighlightTracker is not thread-safe by design. The repository does its
        // own withContext(Dispatchers.IO) for the network hop, so the blocking
        // work is still off the main thread.
        timingJob = scope.launch {
            val timing = BibleAudioRepository.fetchTiming(
                request.versionId,
                request.audioId,
                request.bookId,
                request.chapter_1,
            )
            highlightTracker.setTiming(timing?.verses ?: emptyList())
            timingLoaded = true
            tryInitialSeek()
        }
    }

    /**
     * Performs the deferred start-verse seek requested via
     * [AudioRequest.startVerse_1]. No-op until the player is READY and timing
     * has resolved. Once both are ready: seeks to the verse's start if timing
     * has an entry for it, otherwise clears the pending seek (graceful fallback
     * to position 0). Clearing on every resolved-timing outcome stops a stale
     * verse from seeking a later chapter that happens to lack timing.
     */
    private fun tryInitialSeek() {
        if (pendingStartVerse1 <= 0 || !playerReadyForSeek) return
        val startMs = highlightTracker.getVerseStartMs(pendingStartVerse1)
        if (startMs != null) {
            pendingStartVerse1 = 0
            seekTo(startMs)
        } else if (timingLoaded) {
            pendingStartVerse1 = 0
        }
    }

    /**
     * Seeks playback to the start of verse [verse_1] using the loaded timing.
     * Routes through the same deferred-seek gate as [AudioRequest.startVerse_1]
     * so a pick made while the chapter is still preparing (player not READY or
     * timing not yet fetched) lands once both are ready, instead of being lost.
     * Used when the user picks a verse while this chapter is already playing.
     */
    fun seekToVerse(verse_1: Int) {
        if (!isActive) return
        pendingStartVerse1 = verse_1
        tryInitialSeek()
    }

    fun play() {
        if (!isActive) return
        player.play()
        updateDiagnosticTimer()
    }

    fun pause() {
        if (!isActive) return
        player.pause()
        updateDiagnosticTimer()
    }

    fun seekTo(positionMs: Long) {
        if (!isActive) return
        player.seekTo(positionMs)
        highlightTracker.update(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
        updateDiagnosticTimer()
    }

    /**
     * Skip to the start of the next verse based on timing data. No-op when
     * timing isn't loaded, when we're already past the last verse, or when
     * the player isn't in a seekable state.
     */
    fun seekToNextVerse() {
        if (!isActive) return
        val target = highlightTracker.getNextVerseStartMs(player.currentPositionMs) ?: return
        seekTo(target)
    }

    /**
     * Skip to the previous verse using music-player semantics: a quick tap
     * (within the first ~2 s of a verse) jumps to the prior verse; otherwise
     * it restarts the current verse. See [HighlightTracker.getPrevVerseStartMs].
     */
    fun seekToPrevVerse() {
        if (!isActive) return
        val target = highlightTracker.getPrevVerseStartMs(player.currentPositionMs) ?: return
        seekTo(target)
    }

    /**
     * Returns the `verse_1` that [positionMs] falls inside, or `0` if none.
     * Pure read, used by the slider's live drag preview so the verse highlight
     * in the reader can follow the thumb without committing a seek.
     */
    fun peekVerseAt(positionMs: Long): Int = highlightTracker.peekVerseAt(positionMs)

    fun setSpeed(speed: Float) {
        if (!isActive) return
        player.setSpeed(speed)
        persistedSpeed = speed
        Preferences.setFloat(Prefkey.audioPlaybackSpeed, speed)
        _playbackState.update { it.copy(speed = speed) }
    }

    /**
     * Clears playback and pending work, including loads that never reached READY.
     */
    override fun resetPlayback() {
        loadJob?.cancel()
        timingJob?.cancel()
        positionJob?.cancel()
        cancelDiagnosticTimer()
        currentRequest = null
        hasActiveSession = false
        // Same reason as in [loadChapter]: the in-flight calls being torn down
        // here report their cancellation afterwards, and must not land in the
        // log the IDLE reset just cleared.
        loadGeneration++
        player.logGeneration = loadGeneration
        player.pause()
        player.exoPlayer.stop()
        player.exoPlayer.clearMediaItems()
        _playbackState.value = PlaybackState.IDLE
    }

    /**
     * Loads the chapter immediately before ([direction] = -1) or after
     * ([direction] = 1) the currently-loaded one. No-op when nothing has been
     * loaded, when [direction] is invalid, at the Bible boundary, or at the
     * edge of the selected recording's book coverage (auto-advance stops at a
     * coverage gap instead of skipping it).
     *
     * Used both by:
     *  - the in-app audio bar's prev/next-chapter buttons (via
     *    [yuku.alkitab.base.audio.AudioBarController]), and
     *  - the system transport controls (lock-screen / Bluetooth headset),
     *    routed through [BibleChapterNavigatingPlayer].
     *
     * The activity does not need to be alive for this to work; the
     * [yuku.alkitab.model.Version] is resolved through [App.services]. When the
     * activity *is* alive, `AudioBarController` observes the resulting state
     * change and navigates `IsiActivity` to keep both surfaces in sync.
     */
    fun skipChapter(direction: Int) {
        if (!isActive) return
        val current = currentRequest ?: return
        val resolvedVersion = App.services.versions.getVersionFromVersionId(current.versionId)?.version
        val version = resolvedVersion ?: App.services.versions.activeVersion()
        val selectedSet = AudioSetsRepository.cachedSetsFor(current.versionId)
            ?.sets?.firstOrNull { it.audioId == current.audioId }
        val (book, chapter1) = BibleNeighborResolver.coverageAwareNeighbor(
            version,
            current.bookId,
            current.chapter_1,
            direction,
            selectedSet,
        ) ?: return

        // The version and recording don't change across a chapter skip, so the
        // displaySubtitle carries over as-is, keeping notification metadata
        // stable.
        loadChapter(
            AudioRequest(
                versionId = current.versionId,
                audioId = current.audioId,
                bookId = book.bookId,
                chapter_1 = chapter1,
                displayTitle = "${book.shortName} $chapter1",
                displaySubtitle = current.displaySubtitle,
                startVerse_1 = 0,
            )
        )
    }

    /**
     * Decodes the launcher icon to PNG bytes for use as MediaSession artwork.
     * Goes through [ResourcesCompat.getDrawable] plus draw-into-bitmap because
     * `BitmapFactory.decodeResource` returns null for the adaptive-icon XML
     * (`mipmap-anydpi-v26/ic_launcher.xml`) every production flavor ships.
     *
     * 256 px square keeps the encoded PNG under ~50 KB, so the [MediaMetadata]
     * stays cheap to ship across IPC, and the system downscales it to whatever
     * the lock-screen large-icon slot needs.
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
                // Always recycle, even if compress() throws; long-running
                // services accumulate native bitmap memory otherwise.
                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Failing to decode artwork must not break audio. Fall back to a
            // metadata-only notification.
            AppLog.w(TAG, "Failed to decode launcher icon artwork: ${e.message}")
            null
        }
    }

    /**
     * Records a UI-originated event (a button press on the bar, a bar state
     * transition) into the same log as the HTTP and player events, so the log
     * sheet reads as one chronological account. Called by [AudioBarController].
     */
    fun logUiEvent(message: AudioLogMessage) = appendLog(message)

    /**
     * Resolves [message] against [localizedContext] and appends it to
     * [PlaybackState.logs]. Also serves as [BibleAudioPlayer]'s `onLogEvent`
     * callback, so HTTP connection-state and player-state events land here too,
     * always on the main thread like every other `_playbackState` mutation.
     */
    private fun appendLog(message: AudioLogMessage) = appendLog(loadGeneration, message)

    /**
     * Appends [message] if it belongs to the chapter load currently on screen.
     * Events stamped with an older [generation] come from a superseded load
     * whose HTTP calls are only now reporting their cancellation, and showing
     * them would attribute the previous chapter's teardown to the new one.
     * See [BibleAudioPlayer.logGeneration].
     */
    private fun appendLog(generation: Int, message: AudioLogMessage) {
        if (!isActive || generation != loadGeneration) return
        val entry = logEntry(message)
        _playbackState.update {
            it.copy(logs = appendLogEntry(it.logs, entry, generation, loadGeneration))
        }
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

    private fun updateDiagnosticTimer() {
        val state = _playbackState.value
        if (!state.isActive || state.error != null || !player.isWaitingForPlayback) {
            cancelDiagnosticTimer()
            _playbackState.update { it.copy(isDiagnosticVisible = false) }
            return
        }
        if (state.isDiagnosticVisible) return
        scheduleDiagnostic(
            isWaiting = { player.isWaitingForPlayback },
            onTimeout = { _playbackState.update { it.copy(isDiagnosticVisible = true) } },
        )
    }
}
