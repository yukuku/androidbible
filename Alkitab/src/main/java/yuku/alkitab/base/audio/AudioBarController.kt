package yuku.alkitab.base.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import yuku.alkitab.base.audio.model.AudioSet
import yuku.alkitab.base.audio.ui.AudioBar
import yuku.alkitab.base.audio.ui.AudioBarCommand
import yuku.alkitab.base.audio.ui.AudioBarUiState
import yuku.alkitab.base.audio.ui.AudioSetGroup
import yuku.alkitab.base.audio.ui.AudioSetOption
import yuku.alkitab.base.audio.ui.AudioSourceOption
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.model.Book

/**
 * Glue layer between the View-based [yuku.alkitab.base.IsiActivity] and the
 * Compose [AudioBar] surface, plus the Service-side [BibleAudioService].
 *
 * Responsibilities:
 *  - Bind to the local [BibleAudioService] using [BibleAudioService.ACTION_LOCAL_BIND]
 *    and collect its [BibleAudioService.playbackState] into a UI-shaped flow.
 *  - Project [PlaybackState] + chapter-navigation context into [AudioBarUiState],
 *    keeping the recomposition surface flat.
 *  - Translate Compose [AudioBarCommand]s into service calls + activity navigation.
 *
 * Out of scope for M3 (handled in M4/M5):
 *  - Lock-screen / foreground notification specifics — owned by the service.
 *  - Auto-advance at end of chapter, snackbar errors, speed bottom sheet,
 *    split-view source picker.
 *
 * Lifecycle: the activity calls [attach] in `onCreate` (after `setContentView`)
 * and [detach] in `onDestroy`. Binding to the service is idempotent and uses
 * [Context.BIND_AUTO_CREATE] only when the user actually starts audio — we
 * don't want every IsiActivity instance to spin up a service for users who
 * never tap the audio icon.
 */
class AudioBarController(
    private val context: Context,
) {
    /**
     * The activity-side surface the controller needs to read from to compute
     * chapter labels and to navigate when the user taps prev/next chapter.
     */
    interface Host {
        /** Currently displayed book in the primary split. */
        fun audioCurrentBook(): Book

        /** Currently displayed chapter (1-based). */
        fun audioCurrentChapter1(): Int

        /**
         * The list of version ids currently visible in `IsiActivity` (primary,
         * plus the split-view secondary if open). Drives toolbar-icon visibility.
         */
        fun audioVisibleVersionIds(): List<String>

        /** Visible versions that have audio coverage, ordered split0 → split1. */
        fun audioAvailableSources(): List<AudioSourceOption>

        /** Resolves a book in [versionId]; null if the version isn't visible or doesn't include the book. */
        fun audioBookInVersion(versionId: String, bookId: Int): Book?

        /** Short display name of a visible version, or null when [versionId] isn't on screen. */
        fun audioVersionShortName(versionId: String): String?

        /** Tell the activity to navigate to [book] / [chapter_1] (the existing `display` flow). */
        fun audioDisplayChapter(book: Book, chapter_1: Int)

        /**
         * Fired when the user opens or closes the audio bar — i.e. when an
         * audio session begins or ends. Used by the activity to swap the
         * toolbar audio icon between its inactive and active variants. NOT
         * called for transient state changes (preparing, buffering, seeking)
         * — those flicker too fast to drive a toolbar refresh and are already
         * surfaced by the bar's own play-button spinner.
         */
        fun audioBarVisibilityChanged(visible: Boolean)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private var host: Host? = null
    private var composeView: ComposeView? = null

    private var service: BibleAudioService? = null

    /**
     * Tracks whether [Context.bindService] returned successfully. We MUST keep
     * this flag set until [detach] calls `unbindService`, even after
     * [ServiceConnection.onServiceDisconnected] fires — Android's contract is
     * that the connection still needs to be explicitly unbound, otherwise the
     * `ServiceConnection` leaks.
     */
    private var bound = false
    /** Tracks whether the user has *requested* the bar visible (via [toggle]). */
    private var requestedVisible = false
    /**
     * Set by [reshowIfSessionActive] while we are binding to an
     * already-running service purely to restore the bar after activity
     * recreation / return-from-background. The first projected
     * [PlaybackState] that reports [PlaybackState.isActive] flips the bar back
     * on, then clears this flag. Cleared without showing if the session has
     * ended by the time we connect (no flicker).
     */
    private var reshowPending = false
    /**
     * Set while the user has the slider thumb under their finger. Drives a
     * special-case in [projectToUi]: live playback continues to push a
     * `verse_1` derived from the player's current position every 100 ms, but
     * we don't want that to clobber the drag-preview verse_1 we just wrote
     * from [AudioBarCommand.SeekDrag]. Cleared on [AudioBarCommand.SeekCommit].
     */
    private var dragging = false

    /**
     * The last `(bookId, chapter_1)` pair the *service* reported. We only
     * follow the activity to the service's chapter when this pair *changes*
     * — meaning the change was driven by the service (lock-screen /
     * Bluetooth skip / auto-advance), not by the user manually swiping in
     * the activity. Without this latch, comparing against `host.audio*`
     * would force the reader back to the playing chapter every position
     * tick, breaking manual browsing during playback.
     *
     * `(-1, 0)` is the sentinel for "no chapter loaded yet" and matches
     * [PlaybackState.IDLE].
     */
    private var lastServiceBookId = -1
    private var lastServiceChapter1 = 0

    /** The audio source picked for the current session. Cleared on [hide]. */
    private var selectedSource: AudioSourceOption? = null

    /**
     * 1-based verse the next chapter load should seek to, or `0` for the
     * chapter start. Set by [showFromVerse] (and by [pickSet] when switching
     * recordings mid-verse) and cleared by [dispatchLoad] only once a request
     * actually reaches the service — a request built while the service is
     * still connecting is dropped and rebuilt later from this same field, so
     * clearing it any earlier would lose the requested verse. Chapter-follow
     * loads ([onChapterChanged]) reset it explicitly so browsing always starts
     * at the beginning.
     */
    private var startVerse1 = 0

    private val _uiState = MutableStateFlow(AudioBarUiState.HIDDEN)
    val uiState: StateFlow<AudioBarUiState> = _uiState.asStateFlow()

    /**
     * Whether the toolbar audio icon should be shown for the currently visible
     * version(s). A non-blocking peek: the host builds its source options from
     * [AudioSetsRepository.cachedSetsFor], so an unresolved version reads as
     * unavailable until its fetch lands and the menu is re-prepared (the
     * activity kicks that off — see the async resolution in `IsiActivity`).
     */
    val isAvailable: Boolean
        get() = host?.audioAvailableSources()?.isNotEmpty() == true

    /**
     * Whether the "play audio from this verse" verse action should be offered:
     * at least one visible version has a recording with verse timing that
     * covers the book being read. Without timing there is no verse position to
     * seek to, so the action could only start at the chapter top. Same
     * non-blocking cache peek as [isAvailable].
     */
    val isPlayFromVerseAvailable: Boolean
        get() {
            val host = this.host ?: return false
            return hasPlayableOption(buildSetGroups(host, timedOnly = true))
        }

    /** True while the audio bar is on screen — drives the toolbar audio-icon variant. */
    val isBarVisible: Boolean
        get() = _uiState.value.visible

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? BibleAudioService.LocalBinder ?: return
            // LocalBinder holds the service via WeakReference (to avoid a
            // service leak via the Binder framework's JNI globals); the
            // strong reference we hold in `service` keeps it alive for the
            // lifetime of this binding, so a null here would mean the
            // service was destroyed before we got our connection — bail out
            // and let the next show() rebind.
            val svc = localBinder.service ?: return
            service = svc
            // Note: `bound` is set in [ensureBound] when bindService returns
            // true, NOT here. onServiceConnected is fire-and-forget — if the
            // service crashes before we get here, we still need to unbind.
            collectJob?.cancel()
            collectJob = scope.launch {
                svc.playbackState.collect { state -> projectToUi(state) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            // Do NOT clear `bound` — the ServiceConnection is still registered
            // with the OS until `unbindService` runs in [detach]. Android's
            // contract is that disconnection means the service died; the
            // binding itself is not released.
            collectJob?.cancel()
            collectJob = null
        }
    }

    /**
     * Hooks the controller into the activity. Idempotent — calling twice
     * replaces the host without re-binding the service.
     *
     * The compose content is installed lazily on first [show] (not in `attach`)
     * and disposed in [hide], so the AudioBar's Compose runtime / Recomposer /
     * snapshot machinery is only active while the bar is actually visible —
     * otherwise the verses-list scroll stays measurably warmer.
     */
    fun attach(host: Host, composeView: ComposeView) {
        this.host = host
        this.composeView = composeView
    }

    /**
     * Restores the audio bar when the activity becomes visible again (rotation,
     * process/activity recreation, or return-from-background) while the service
     * is still mid-session. Call from `IsiActivity.onStart` — it covers both a
     * fresh activity (after [attach]) and a returning one on the same instance.
     *
     * Binds only when [BibleAudioService.hasActiveSession] is already true, so
     * we never spin the service up for users who haven't started audio. The
     * actual reshow happens in [projectToUi] once the first [PlaybackState]
     * arrives, avoiding a show-then-hide flicker if the session just ended.
     */
    fun reshowIfSessionActive() {
        if (!shouldBindForReshow(requestedVisible, BibleAudioService.hasActiveSession)) return
        reshowPending = true
        ensureBound()
    }

    private var composeContentInstalled = false

    private fun ensureComposeContent() {
        val cv = composeView ?: return
        if (composeContentInstalled) return
        cv.setContent {
            val state by uiState.collectAsState()
            AudioBar(state = state, onCommand = ::onCommand, modifier = Modifier)
        }
        composeContentInstalled = true
    }

    private fun teardownComposeContent() {
        val cv = composeView ?: return
        if (!composeContentInstalled) return
        cv.disposeComposition()
        composeContentInstalled = false
    }

    /**
     * Called when the active version changes (split toggled, version swapped).
     * Stops audio if the source we picked is no longer on screen.
     */
    fun onActiveVersionChanged() {
        val host = this.host ?: return
        val source = selectedSource
        if (source != null && source.versionId !in host.audioVisibleVersionIds()) {
            hide()
        }
    }

    /**
     * Called from `IsiActivity.display()` so the audio follows the reader.
     * Closes the bar if the new book is not in the selected version, or not
     * covered by the selected recording — loading it would only 404, and
     * silently switching recordings mid-browse is worse than closing.
     */
    fun onChapterChanged() {
        if (!requestedVisible) return
        val host = this.host ?: return
        val source = selectedSource ?: return
        val readerBook = host.audioCurrentBook()
        val chapter1 = host.audioCurrentChapter1()
        val sourceBook = host.audioBookInVersion(source.versionId, readerBook.bookId)
        if (sourceBook == null) {
            hide()
            return
        }
        val set = selectedSet()
        if (set != null && !set.coversBook(sourceBook.bookId)) {
            hide()
            return
        }
        val availableChapter = chapter1.coerceIn(1, sourceBook.chapter_count)

        if (sourceBook.bookId == lastServiceBookId && availableChapter == lastServiceChapter1) {
            return
        }

        // Bump the latch before loadChapter so projectToUi treats the resulting
        // state as activity-driven and skips the reverse host.audioDisplayChapter call.
        lastServiceBookId = sourceBook.bookId
        lastServiceChapter1 = availableChapter

        // Chapter browsing supersedes any not-yet-delivered play-from-verse target.
        startVerse1 = 0
        val request = BibleAudioService.AudioRequest(
            versionId = source.versionId,
            audioId = source.audioId,
            bookId = sourceBook.bookId,
            chapter_1 = availableChapter,
            displayTitle = "${sourceBook.shortName} $availableChapter",
            displaySubtitle = displaySubtitle(source),
            startVerse_1 = 0,
        )
        dispatchLoad(host, request)
    }

    /**
     * Toggles the bar. If hidden: bind the service (if not already bound),
     * issue a [BibleAudioService.loadChapter] for the current book/chapter,
     * and slide the bar in. If visible: stop audio and slide out.
     */
    fun toggle() {
        if (requestedVisible) {
            hide()
        } else {
            show()
        }
    }

    /**
     * Opens the bar and starts playback seeked to [verse_1] (1-based) — the
     * "play audio from this verse" verse action.
     *
     * - Session already on the reader's chapter with a recording that has
     *   timing (or whose timing is still unresolved): plain seek, no reload.
     * - No session, and every recording that would play has timing: the normal
     *   [show] flow (direct start, or the split-view source picker), carrying
     *   the start verse.
     * - Otherwise the recording that would play is known to lack timing — a
     *   verse seek in it cannot resolve — so the recording sheet opens listing
     *   only recordings with timing (grouped per version, like the bar's
     *   recording chip), and the pick starts playback at the verse.
     */
    fun showFromVerse(verse_1: Int) {
        val host = this.host ?: return
        val svc = service
        val sessionOnTimedSet = requestedVisible && selectedSource != null &&
            selectedSet()?.hasTiming != false
        if (sessionOnTimedSet && svc != null &&
            host.audioCurrentBook().bookId == lastServiceBookId &&
            host.audioCurrentChapter1() == lastServiceChapter1
        ) {
            svc.seekToVerse(verse_1)
            return
        }
        startVerse1 = verse_1

        if (sessionOnTimedSet) {
            // Session on a chapter other than the reader's (or the service is
            // still connecting): reload the reader's chapter at the verse.
            startSession(host)
            return
        }

        if (!requestedVisible) {
            val sources = host.audioAvailableSources()
            if (sources.isNotEmpty() && sources.all { resolvedSet(it)?.hasTiming == true }) {
                show()
                return
            }
        }

        val groups = buildSetGroups(host, timedOnly = true)
        if (groups.isEmpty()) {
            // The verse action is hidden when no timed recording is visible;
            // guard against a set list refreshed between menu prep and tap.
            AppLog.w(TAG, "showFromVerse: no recording with timing on screen")
            startVerse1 = 0
            if (!requestedVisible) show()
            return
        }
        requestedVisible = true
        ensureComposeContent()
        _uiState.update { it.copy(setGroups = groups) }
        host.audioBarVisibilityChanged(true)
    }

    fun show() {
        val host = this.host ?: return
        requestedVisible = true
        ensureComposeContent()

        val sources = host.audioAvailableSources()
        when (sources.size) {
            0 -> {
                // Should be unreachable — menu icon is hidden when no source has audio.
                AppLog.w(TAG, "show() called with no audio sources visible")
                requestedVisible = false
                startVerse1 = 0
            }
            1 -> {
                selectedSource = sources[0]
                startSession(host)
            }
            else -> {
                // Split view + both sides have audio: show the picker dialog
                // instead of starting immediately.
                _uiState.update {
                    AudioBarUiState.HIDDEN.copy(visible = false, pickerOptions = sources)
                }
                host.audioBarVisibilityChanged(true)
            }
        }
    }

    /** Binds the service and fires the first loadChapter once [selectedSource] is set. */
    private fun startSession(host: Host) {
        requestedVisible = true
        ensureComposeContent()
        ensureBound()
        _uiState.update {
            it.copy(visible = true, preparing = service == null, pickerOptions = null, setGroups = null)
        }
        host.audioBarVisibilityChanged(true)
        val request = buildRequest(host) ?: return
        dispatchLoad(host, request)
    }

    fun hide() {
        requestedVisible = false
        reshowPending = false
        dragging = false
        selectedSource = null
        startVerse1 = 0
        lastServiceBookId = -1
        lastServiceChapter1 = 0
        service?.stop()
        _uiState.update { AudioBarUiState.HIDDEN }
        host?.audioBarVisibilityChanged(false)
        teardownComposeContent()
        // Unbind so [BibleAudioService] can destroy, releasing ExoPlayer /
        // MediaSession / foreground notification. Keeping the binding alive
        // between shows ("avoid bind/unbind churn") left enough audio-stack
        // overhead resident to make verses-list scroll measurably laggier
        // after the bar was dismissed.
        if (bound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "unbindService on hide: ${e.message}")
            }
            bound = false
            service = null
            collectJob?.cancel()
            collectJob = null
        }
    }

    /**
     * Releases activity references. Called from `IsiActivity.onDestroy`.
     * Does NOT call `service.stop()` — the service is independently owned and
     * may continue playing when the activity is recreated (M4 lock-screen).
     */
    fun detach() {
        reshowPending = false
        if (bound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "unbindService raced with onDestroy: ${e.message}")
            }
            bound = false
        }
        service = null
        collectJob?.cancel()
        collectJob = null
        scope.cancel()
        host = null
        composeView = null
        // Drop the pending Host reference too — otherwise an in-flight load
        // queued before the service connected leaks the activity.
        pendingLoad = null
    }

    // -- internals --------------------------------------------------------------

    private var pendingLoad: Host? = null

    private fun ensureBound() {
        if (bound) return
        // We need the service to outlive the activity (rotation, backgrounding,
        // lock screen) — a pure `bindService` would die the moment we unbind.
        // `startService` keeps the service alive without starting the 5-second
        // `startForeground` deadline, and media3's `MediaNotificationManager`
        // promotes us to foreground itself the moment the player enters a
        // user-engaged state (BUFFERING/READY): it calls
        // `ContextCompat.startForegroundService(...)` + `Service.startForeground`
        // back-to-back inside the same main-thread frame, so the system's
        // foreground-service rules are satisfied without us posting anything.
        // The `mediaPlayback` foreground-service-type exemption covers the
        // background-start restriction on Android 12+.
        val startIntent = Intent(context, BibleAudioService::class.java)
        context.startService(startIntent)
        val bindIntent = Intent(context, BibleAudioService::class.java)
            .setAction(BibleAudioService.ACTION_LOCAL_BIND)
        try {
            // bindService can return false if the service can't be found or
            // the system refuses to deliver. Only flip `bound` on success so
            // [detach] doesn't try to unbind a connection that was never
            // registered.
            if (context.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                bound = true
            } else {
                AppLog.w(TAG, "bindService returned false — service not bound")
            }
        } catch (e: SecurityException) {
            // Should never happen — we own the service. Logged for paranoia.
            AppLog.e(TAG, "bindService denied: ${e.message}")
        }
    }

    /** Returns null when the selected version doesn't include the reader's current book. */
    private fun buildRequest(host: Host): BibleAudioService.AudioRequest? {
        val source = selectedSource ?: return null
        val readerBook = host.audioCurrentBook()
        val chapter1 = host.audioCurrentChapter1()
        val sourceBook = host.audioBookInVersion(source.versionId, readerBook.bookId)
            ?: return null
        val availableChapter = chapter1.coerceIn(1, sourceBook.chapter_count)
        return BibleAudioService.AudioRequest(
            versionId = source.versionId,
            audioId = source.audioId,
            bookId = sourceBook.bookId,
            chapter_1 = availableChapter,
            displayTitle = "${sourceBook.shortName} $availableChapter",
            displaySubtitle = displaySubtitle(source),
            startVerse_1 = startVerse1,
        )
    }

    /**
     * Hands [request] to the service, or queues a rebuild for when the service
     * connects. [startVerse1] is cleared only when the request is actually
     * delivered: the queued path rebuilds the request in [projectToUi] via
     * [buildRequest], which must still see the requested start verse — this is
     * what makes "play audio from this verse" survive the initial service
     * binding instead of degrading to a chapter-top start.
     */
    private fun dispatchLoad(host: Host, request: BibleAudioService.AudioRequest) {
        val svc = service
        if (svc == null) {
            pendingLoad = host
            return
        }
        svc.loadChapter(request)
        startVerse1 = 0
    }

    /** The [AudioSet] behind [option], or null while its version's set list is unresolved. */
    private fun resolvedSet(option: AudioSourceOption): AudioSet? =
        AudioSetsRepository.cachedSetsFor(option.versionId)
            ?.sets?.firstOrNull { it.audioId == option.audioId }

    /**
     * The recording selected for the current session, resolved against the
     * cached set list. Null while nothing is selected or the cache is cold
     * (e.g. right after process death).
     */
    private fun selectedSet(): AudioSet? = selectedSource?.let(::resolvedSet)

    /** Set list of the selected source's version, or null while unresolved. */
    private fun setsOfSelectedVersion(): List<AudioSet>? {
        val source = selectedSource ?: return null
        return AudioSetsRepository.cachedSetsFor(source.versionId)?.sets
    }

    /**
     * Notification subtitle: `"<version short name> · <set title>"` when the
     * version has more than one recording, the bare version name otherwise.
     */
    private fun displaySubtitle(source: AudioSourceOption): String {
        val sets = AudioSetsRepository.cachedSetsFor(source.versionId)?.sets
        return if (sets != null && sets.size > 1) "${source.shortName} · ${source.title}" else source.shortName
    }

    private fun onCommand(cmd: AudioBarCommand) {
        val svc = service
        val host = this.host ?: return
        when (cmd) {
            is AudioBarCommand.PickSource -> {
                val sources = host.audioAvailableSources()
                val picked = sources.firstOrNull { it.versionId == cmd.versionId } ?: return
                selectedSource = picked
                startSession(host)
            }
            AudioBarCommand.CancelPicker -> hide()
            AudioBarCommand.PlayPause -> {
                if (svc == null) return
                if (_uiState.value.isPlaying) svc.pause() else svc.play()
            }
            AudioBarCommand.PrevVerse -> svc?.seekToPrevVerse()
            AudioBarCommand.NextVerse -> svc?.seekToNextVerse()
            AudioBarCommand.Close -> hide()
            AudioBarCommand.Speed -> {
                _uiState.update { it.copy(showSpeedSheet = true) }
            }
            AudioBarCommand.DismissSpeedSheet -> {
                _uiState.update { it.copy(showSpeedSheet = false) }
            }
            AudioBarCommand.OpenSetSheet -> {
                val groups = buildSetGroups(host, timedOnly = false)
                if (groups.isEmpty()) return
                _uiState.update { it.copy(setGroups = groups) }
            }
            AudioBarCommand.DismissSetSheet -> {
                if (selectedSource == null) {
                    // The play-from-verse picker was dismissed before any
                    // session existed — nothing to keep on screen.
                    hide()
                } else {
                    startVerse1 = 0
                    _uiState.update { it.copy(setGroups = null) }
                }
            }
            is AudioBarCommand.PickSet -> pickSet(host, cmd.versionId, cmd.audioId)
            AudioBarCommand.Retry -> retryLoad(host)
            is AudioBarCommand.SetSpeed -> {
                svc?.setSpeed(cmd.speed)
                _uiState.update { it.copy(speed = cmd.speed, showSpeedSheet = false) }
            }
            is AudioBarCommand.SeekDrag -> {
                // The slider thumb's mm:ss is owned by AudioBar's local drag
                // state, but we DO push a freshly-resolved verse_1 into the
                // shared UI state so the verse highlight + smooth-scroll
                // follow the dragging finger live. peekVerseAt is a pure read
                // — no playback side effects. The `dragging` flag tells
                // projectToUi to leave verse_1 alone while the finger is down,
                // otherwise the 100 ms playback poll would overwrite our
                // drag-preview verse twice a second.
                dragging = true
                val previewVerse = svc?.peekVerseAt(cmd.positionMs) ?: return
                _uiState.update { it.copy(verse_1 = previewVerse) }
            }
            is AudioBarCommand.SeekCommit -> {
                dragging = false
                svc?.seekTo(cmd.positionMs)
            }
            AudioBarCommand.OpenLogSheet -> {
                _uiState.update { it.copy(showLogSheet = true) }
            }
            AudioBarCommand.DismissLogSheet -> {
                _uiState.update { it.copy(showLogSheet = false) }
            }
        }
    }

    /**
     * The recording-picker sheet's content: one group per visible version
     * whose set list has resolved non-empty — two groups in split view when
     * both sides have audio. Rows for sets (or versions) that don't cover the
     * book being read are listed but disabled. With [timedOnly], recordings
     * without verse timing are dropped entirely (the play-from-verse picker —
     * an untimed recording cannot serve a verse seek, so listing it disabled
     * would only advertise a dead end).
     */
    private fun buildSetGroups(host: Host, timedOnly: Boolean): List<AudioSetGroup> {
        val currentBookId = host.audioCurrentBook().bookId
        return buildSetGroups(
            versionIds = host.audioVisibleVersionIds().distinct(),
            currentBookId = currentBookId,
            selectedVersionId = selectedSource?.versionId,
            selectedAudioId = selectedSource?.audioId,
            timedOnly = timedOnly,
            setsOf = { versionId -> AudioSetsRepository.cachedSetsFor(versionId)?.sets },
            versionNameOf = host::audioVersionShortName,
            versionHasBook = { versionId -> host.audioBookInVersion(versionId, currentBookId) != null },
        )
    }

    /**
     * Switches the session to another recording — of the same version, or of
     * the other split's version (the sheet lists both in split view, so this
     * is also the mid-session way to move audio across the splits). Persists
     * the per-version choice, then reloads the current chapter in the new
     * recording. The start verse: a pending play-from-verse target wins, then
     * continuity with the verse that was playing; a recording without timing
     * always starts at the chapter top. Timing differs per recording, so a
     * millisecond-preserving switch would land in an arbitrary place.
     *
     * Also serves the play-from-verse picker's pick, including before any
     * session exists ([selectedSource] still null) — [startSession] then
     * binds the service and shows the bar.
     */
    private fun pickSet(host: Host, versionId: String, audioId: String) {
        val source = selectedSource
        if (source != null && versionId == source.versionId && audioId == source.audioId) {
            _uiState.update { it.copy(setGroups = null) }
            // Re-picked the recording already playing while a play-from-verse
            // target is pending: a seek does the job.
            val sv = startVerse1
            if (sv > 0) {
                startVerse1 = 0
                service?.seekToVerse(sv)
            }
            return
        }
        val newSet = AudioSetsRepository.cachedSetsFor(versionId)?.sets
            ?.firstOrNull { it.audioId == audioId } ?: return
        val versionName = host.audioVersionShortName(versionId) ?: return
        AudioSetSelections.store(versionId, newSet.audioId)
        startVerse1 = startVerseForSetSwitch(
            newSetHasTiming = newSet.hasTiming,
            pendingStartVerse1 = startVerse1,
            playingVerse1 = _uiState.value.verse_1,
        )
        selectedSource = AudioSourceOption(
            versionId = versionId,
            shortName = versionName,
            audioId = newSet.audioId,
            title = newSet.title,
        )
        startSession(host)
    }

    /**
     * Retries a failed chapter load from scratch. The cached set answer for
     * the source version is dropped after the request is built, so a failure
     * that came from a set resolution cached as "no audio" (e.g. the network
     * was down at fetch time) gets a fresh query on this explicitly
     * user-initiated retry.
     */
    private fun retryLoad(host: Host) {
        val source = selectedSource ?: return
        val request = buildRequest(host) ?: return
        AudioSetsRepository.invalidate(source.versionId)
        dispatchLoad(host, request)
    }

    private fun projectToUi(state: PlaybackState) {
        val host = this.host

        // Auto-reshow after activity recreation / return-from-background: the
        // service is still mid-session but the bar was reset to hidden. Flip it
        // back on once the first active state lands, reconstructing the session
        // source from the service's loaded versionId. Done before the
        // _uiState.update below so `visible` picks it up in the same emission.
        if (reshowPending) {
            val reshow = shouldReshowNow(reshowPending, state)
            // One-shot: consume the flag on the first state after binding,
            // whether or not we actually reshow. If the session ended before we
            // connected (!state.isActive) we just drop it — no show-then-hide
            // flicker against an idle service.
            reshowPending = false
            if (reshow) {
                requestedVisible = true
                ensureComposeContent()
                host?.audioAvailableSources()
                    ?.firstOrNull { it.versionId == state.versionId }
                    ?.let { option ->
                        // Prefer the recording the service is actually playing
                        // over the option's (selection-derived) one, so the bar
                        // reflects the live session even if the persisted
                        // selection changed while we were away.
                        val playingSet = AudioSetsRepository.cachedSetsFor(option.versionId)
                            ?.sets?.firstOrNull { it.audioId == state.audioId }
                        selectedSource = if (playingSet != null) {
                            option.copy(audioId = playingSet.audioId, title = playingSet.title)
                        } else {
                            option
                        }
                    }
                host?.audioBarVisibilityChanged(true)
            }
        }

        // Snapshot before we drain — if a load was queued before the service
        // connected, the first incoming state is usually `IDLE`, which would
        // briefly clear the spinner before our loadChapter call sets it back
        // to preparing. Holding the spinner true until we've fired the
        // queued load avoids that flicker.
        val isPending = pendingLoad != null
        pendingLoad?.let { ph ->
            pendingLoad = null
            buildRequest(ph)?.let { req -> dispatchLoad(ph, req) }
        }

        // A coordinator-driven external stop (a hymn took over audio) resets the
        // service to IDLE. Tear the bar down so it doesn't linger in a dead,
        // un-resumable state. `isPending` guards the startup race where the
        // freshly-created service replays IDLE before our queued loadChapter runs.
        if (!isPending && requestedVisible && state == PlaybackState.IDLE) {
            hide()
            return
        }

        // Sync the activity to the chapter the service is now playing. Only fires
        // for service-driven changes (lock screen / Bluetooth / auto-advance);
        // activity-driven changes from onChapterChanged bump the latch first.
        if (host != null && state.bookId >= 0 &&
            (state.bookId != lastServiceBookId || state.chapter_1 != lastServiceChapter1)
        ) {
            lastServiceBookId = state.bookId
            lastServiceChapter1 = state.chapter_1
            val hostBook = host.audioCurrentBook()
            if (hostBook.bookId != state.bookId || host.audioCurrentChapter1() != state.chapter_1) {
                val targetBook = host.audioVisibleVersionIds()
                    .firstNotNullOfOrNull { host.audioBookInVersion(it, state.bookId) }
                if (targetBook != null) {
                    host.audioDisplayChapter(targetBook, state.chapter_1)
                }
            }
        }

        val playingVersionId = state.versionId.takeIf { it.isNotEmpty() }
            ?: selectedSource?.versionId

        val selectedSet = selectedSet()
        // The recording chip appears whenever the sheet has a choice to offer:
        // several recordings of the source version, or (split view) a second
        // version with audio to move the source to.
        val audioCapableVersions = host?.audioVisibleVersionIds()?.distinct()
            ?.count { AudioSetsRepository.cachedSetsFor(it)?.sets?.isNotEmpty() == true } ?: 0
        val setTitle = when {
            selectedSet == null -> null
            (setsOfSelectedVersion()?.size ?: 0) > 1 || audioCapableVersions > 1 -> selectedSet.title
            else -> null
        }

        val effectivePreparing = state.preparing || isPending
        _uiState.update { current ->
            current.copy(
                visible = requestedVisible && current.pickerOptions == null,
                isPlaying = state.isPlaying,
                preparing = effectivePreparing,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                // Keep our drag-preview verse_1 while the user is dragging;
                // otherwise let the live playback verse drive the highlight.
                verse_1 = if (dragging) current.verse_1 else state.verse_1,
                speed = state.speed,
                error = state.error,
                logs = state.logs,
                timingAvailable = computeTimingAvailable(
                    setHasTiming = selectedSet?.hasTiming,
                    stateVerse1 = state.verse_1,
                    currentTimingAvailable = current.timingAvailable,
                ),
                playingVersionId = playingVersionId,
                setTitle = setTitle,
            )
        }
    }

    companion object {
        private const val TAG = "AudioBarController"

        /**
         * Whether [reshowIfSessionActive] should bind to the service: only when
         * the bar isn't already managed by this controller ([requestedVisible])
         * and the service reports an active session. Pure for unit testing.
         */
        internal fun shouldBindForReshow(requestedVisible: Boolean, hasActiveSession: Boolean): Boolean =
            !requestedVisible && hasActiveSession

        /**
         * Whether a pending reshow should fire for [state]: only once the first
         * active state arrives. Pure for unit testing.
         */
        internal fun shouldReshowNow(reshowPending: Boolean, state: PlaybackState): Boolean =
            reshowPending && state.isActive

        /**
         * Whether verse highlight and verse-skip should be enabled. A selected
         * recording known to have no timing ([setHasTiming] = false) disables
         * them from the start — knowable before playback from the set list —
         * rather than leaving them to go inert once an empty timing fetch
         * returns. Otherwise (timing expected, or the set unknown on a cold
         * cache) they enable once the first non-zero verse hit arrives and
         * latch on. Pure for unit testing.
         */
        internal fun computeTimingAvailable(
            setHasTiming: Boolean?,
            stateVerse1: Int,
            currentTimingAvailable: Boolean,
        ): Boolean = when (setHasTiming) {
            false -> false
            else -> stateVerse1 > 0 || currentTimingAvailable
        }

        /**
         * Builds the recording-picker groups from plain data: one group per
         * version whose (optionally timing-filtered) set list is non-empty, in
         * the given version order. Pure so the grouping, [timedOnly]
         * filtering, selection marking, and coverage flags are unit-testable
         * without a [Host] or the repository cache.
         */
        internal fun buildSetGroups(
            versionIds: List<String>,
            currentBookId: Int,
            selectedVersionId: String?,
            selectedAudioId: String?,
            timedOnly: Boolean,
            setsOf: (versionId: String) -> List<AudioSet>?,
            versionNameOf: (versionId: String) -> String?,
            versionHasBook: (versionId: String) -> Boolean,
        ): List<AudioSetGroup> = versionIds.mapNotNull { versionId ->
            val sets = setsOf(versionId)
                ?.let { sets -> if (timedOnly) sets.filter { it.hasTiming } else sets }
            if (sets.isNullOrEmpty()) return@mapNotNull null
            val versionName = versionNameOf(versionId) ?: return@mapNotNull null
            val bookInVersion = versionHasBook(versionId)
            AudioSetGroup(
                versionId = versionId,
                versionName = versionName,
                options = sets.map { set ->
                    AudioSetOption(
                        audioId = set.audioId,
                        title = set.title,
                        selected = versionId == selectedVersionId && set.audioId == selectedAudioId,
                        coversCurrentBook = bookInVersion && set.coversBook(currentBookId),
                    )
                },
            )
        }

        /**
         * Whether any listed recording can actually serve the book being read —
         * gates the "play audio from this verse" verse action when applied to
         * timing-filtered groups. Pure for unit testing.
         */
        internal fun hasPlayableOption(groups: List<AudioSetGroup>): Boolean =
            groups.any { group -> group.options.any { it.coversCurrentBook } }

        /**
         * Start verse for a recording switch: an explicitly requested verse
         * (play-from-verse) wins, then continuity with the verse that was
         * playing; a recording without timing always starts at the chapter
         * top, since a verse seek in it cannot resolve. Pure for unit testing.
         */
        internal fun startVerseForSetSwitch(
            newSetHasTiming: Boolean,
            pendingStartVerse1: Int,
            playingVerse1: Int,
        ): Int = when {
            !newSetHasTiming -> 0
            pendingStartVerse1 > 0 -> pendingStartVerse1
            playingVerse1 > 0 -> playingVerse1
            else -> 0
        }
    }
}
