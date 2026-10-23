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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import yuku.alkitab.base.audio.model.AudioSet
import yuku.alkitab.base.audio.ui.AudioBar
import yuku.alkitab.base.audio.ui.AudioBarCommand
import yuku.alkitab.base.audio.ui.AudioBarUiState
import yuku.alkitab.base.audio.ui.AudioSetGroup
import yuku.alkitab.base.audio.ui.AudioSetOption
import yuku.alkitab.base.audio.ui.AudioSourceOption
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book

/**
 * Glue layer between the View-based [yuku.alkitab.base.IsiActivity], the Compose
 * [AudioBar] surface and the Service-side [BibleAudioService].
 *
 * The activity calls [attach] in `onCreate` (after `setContentView`) and
 * [detach] in `onDestroy`. Binding to the service is idempotent and happens only
 * once the user starts audio, so an IsiActivity never spins a service up for
 * someone who does not tap the audio icon.
 */
class AudioBarController(private val context: Context) {
    interface Host {
        fun audioCurrentBook(): Book

        /** The chapter the reader is showing, 1-based. */
        fun audioCurrentChapter1(): Int

        /** Primary, plus the split-view secondary when open. Drives toolbar-icon visibility. */
        fun audioVisibleVersionIds(): List<String>

        /** Visible versions that have audio coverage, ordered split0 then split1. */
        fun audioAvailableSources(): List<AudioSourceOption>

        /** Resolves a book in [versionId]; null if the version isn't visible or doesn't include the book. */
        fun audioBookInVersion(versionId: String, bookId: Int): Book?

        /** Short display name of a visible version, or null when [versionId] isn't on screen. */
        fun audioVersionShortName(versionId: String): String?

        fun audioDisplayChapter(book: Book, chapter_1: Int)

        /**
         * Fired when an audio session begins or ends, so the activity can swap
         * the toolbar audio icon between its inactive and active variants. NOT
         * called for transient state changes (preparing, buffering, seeking):
         * those flicker too fast to drive a toolbar refresh and are already
         * surfaced by the bar's own play-button spinner.
         */
        fun audioBarVisibilityChanged(visible: Boolean)
    }

    private sealed interface Connection {
        data object Unbound : Connection
        data object Binding : Connection
        data class Connected(val service: BibleAudioService) : Connection
        /** The binding remains registered until unbindService, even after the service disconnects. */
        data object Disconnected : Connection
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var host: Host? = null
    private var composeView: ComposeView? = null
    private var connection: Connection = Connection.Unbound
    private var session = AudioBarSession.HIDDEN
    private var isNavigatingToPassage = false
    private var isProcessingTransition = false
    private val transitions = ArrayDeque<() -> Unit>()
    private var isComposeContentInstalled = false
    private val _uiState = MutableStateFlow(AudioBarUiState.HIDDEN)
    val uiState: StateFlow<AudioBarUiState> = _uiState.asStateFlow()

    private val service: BibleAudioService?
        get() = (connection as? Connection.Connected)?.service

    val isAvailable: Boolean
        get() = host?.audioAvailableSources()?.isNotEmpty() == true

    val isPlayFromVerseAvailable: Boolean
        get() = host?.let { hasPlayableOption(buildSetGroups(it, isTimedOnly = true)) } == true

    val isBarVisible: Boolean
        get() = uiState.value.visible

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val connected = (binder as? BibleAudioService.LocalBinder)?.service
            transition {
                if (connection == Connection.Unbound) return@transition
                if (connected == null) {
                    closeSession(isStopPlayback = false)
                    return@transition
                }
                connection = Connection.Connected(connected)
                collectJob?.cancel()
                collectJob = scope.launch {
                    connected.playbackState.collect { snapshot ->
                        transition { receivePlayback(connected, snapshot) }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            transition {
                if (connection == Connection.Unbound) return@transition
                connection = Connection.Disconnected
                collectJob?.cancel()
                collectJob = null
                session = session.copy(
                    preview = null,
                    pending = session.pending ?: AudioBarPendingAction.Restore,
                    playback = session.playback.copy(isPlaying = false, preparing = true),
                )
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            transition { closeSession(isStopPlayback = false) }
        }

        override fun onNullBinding(name: ComponentName?) {
            transition { closeSession(isStopPlayback = false) }
        }
    }

    fun attach(host: Host, composeView: ComposeView) {
        this.host = host
        this.composeView = composeView
    }

    fun reshowIfSessionActive() = transition {
        if (session.isRequestedVisible || !BibleAudioService.hasActiveSession) return@transition
        session = session.copy(isRequestedVisible = true, pending = AudioBarPendingAction.Restore)
        ensureBound()
    }

    fun onActiveVersionChanged() = transition {
        val currentHost = host ?: return@transition
        val source = session.source
        if (source != null && source.versionId !in currentHost.audioVisibleVersionIds()) {
            closeSession(isStopPlayback = true)
        } else {
            refreshChoices()
        }
    }

    fun navigateToPassage(navigate: () -> Unit) = transition {
        val isAudioActive = session.isRequestedVisible || BibleAudioService.hasActiveSession
        isNavigatingToPassage = true
        try {
            service?.pause()
            navigate()
        } finally {
            isNavigatingToPassage = false
        }
        if (!isAudioActive) return@transition
        val target = currentTarget() ?: return@transition
        session = session.copy(
            isRequestedVisible = true,
            pending = AudioBarPendingAction.FollowReader(target, isAutoPlay = false),
            dialog = AudioBarDialog.None,
            preview = null,
        )
        if (ensureBound()) performPendingAction()
    }

    fun onChapterChanged() {
        if (isNavigatingToPassage) return
        transition {
            if (!session.isRequestedVisible) return@transition
            val target = currentTarget() ?: return@transition
            if (session.pending == null && target.matches(session.playback)) return@transition
            session = session.copy(
                pending = AudioBarPendingAction.FollowReader(target, isAutoPlay = true),
                dialog = AudioBarDialog.None,
                preview = null,
            )
            if (ensureBound()) performPendingAction()
        }
    }

    fun toggle() {
        if (session.isRequestedVisible) hide() else show()
    }

    fun show() = transition { showAtVerse(0) }

    fun showFromVerse(verse_1: Int) = transition {
        val currentHost = host ?: return@transition
        val target = currentTarget() ?: return@transition
        val connected = service
        if (session.source != null && selectedSet()?.hasTiming != false &&
            session.pending == null && target.matches(session.playback) && connected != null
        ) {
            session = session.copy(dialog = AudioBarDialog.None, preview = null)
            connected.seekToVerse(verse_1)
            connected.play()
            return@transition
        }
        if (session.source != null && selectedSet()?.hasTiming != false) {
            val source = session.source ?: return@transition
            queueLoad(source, target, verse_1, isAutoPlay = true)
            return@transition
        }
        val sources = currentHost.audioAvailableSources()
        if (!session.isRequestedVisible && sources.isNotEmpty() && sources.all { resolvedSet(it)?.hasTiming == true }) {
            showAtVerse(verse_1)
            return@transition
        }
        if (buildSetGroups(currentHost, isTimedOnly = true).isEmpty()) return@transition
        session = session.copy(
            isRequestedVisible = true,
            pending = null,
            dialog = AudioBarDialog.Recording(target, verse_1, isTimedOnly = true),
            preview = null,
        )
    }

    private fun showAtVerse(startVerse1: Int) {
        val currentHost = host ?: return
        val target = currentTarget() ?: return
        val sources = currentHost.audioAvailableSources()
        when (sources.size) {
            0 -> closeSession(isStopPlayback = false)
            1 -> queueLoad(sources.single(), target, startVerse1, isAutoPlay = true)
            else -> session = session.copy(
                isRequestedVisible = true,
                pending = null,
                dialog = AudioBarDialog.Source(target, startVerse1),
                preview = null,
            )
        }
    }

    fun hide() = transition { closeSession(isStopPlayback = true) }

    fun detach() {
        closeSession(isStopPlayback = false)
        publish()
        scope.cancel()
        transitions.clear()
        host = null
        composeView = null
    }

    private fun closeSession(isStopPlayback: Boolean) {
        val connected = service
        val isRegistered = connection != Connection.Unbound
        session = AudioBarSession.HIDDEN
        collectJob?.cancel()
        collectJob = null
        connection = Connection.Unbound
        if (isStopPlayback) connected?.stop()
        if (isRegistered) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "unbindService: ${e.message}")
            }
        }
    }

    private fun ensureBound(): Boolean {
        if (connection != Connection.Unbound) return true
        connection = Connection.Binding
        try {
            context.startService(Intent(context, BibleAudioService::class.java))
            val intent = Intent(context, BibleAudioService::class.java).setAction(BibleAudioService.ACTION_LOCAL_BIND)
            if (context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) return true
            AppLog.w(TAG, "bindService returned false")
        } catch (e: SecurityException) {
            AppLog.e(TAG, "bindService denied: ${e.message}")
        } catch (e: IllegalStateException) {
            AppLog.e(TAG, "startService denied: ${e.message}")
        }
        connection = Connection.Unbound
        closeSession(isStopPlayback = false)
        return false
    }

    private fun currentTarget(): AudioBarTarget? {
        val currentHost = host ?: return null
        return AudioBarTarget(currentHost.audioCurrentBook().bookId, currentHost.audioCurrentChapter1())
    }

    private fun queueLoad(source: AudioSourceOption, target: AudioBarTarget, startVerse1: Int, isAutoPlay: Boolean) {
        val request = buildRequest(source, target, startVerse1, isAutoPlay)
        if (request == null) {
            closeSession(isStopPlayback = true)
            return
        }
        queueRequest(source, request)
    }

    private fun buildRequest(source: AudioSourceOption, target: AudioBarTarget, startVerse1: Int, isAutoPlay: Boolean): BibleAudioService.AudioRequest? {
        val currentHost = host ?: return null
        val book = currentHost.audioBookInVersion(source.versionId, target.bookId)
        val set = resolvedSet(source)
        if (book == null || set?.coversBook(target.bookId) == false) {
            return null
        }
        val chapter1 = target.chapter1.coerceIn(1, book.chapter_count)
        return BibleAudioService.AudioRequest(
            versionId = source.versionId,
            audioId = source.audioId,
            bookId = book.bookId,
            chapter_1 = chapter1,
            displayTitle = "${book.shortName} $chapter1",
            displaySubtitle = displaySubtitle(source),
            startVerse_1 = startVerse1,
            isAutoPlay = isAutoPlay,
        )
    }

    private fun queueRequest(source: AudioSourceOption, request: BibleAudioService.AudioRequest) {
        session = session.copy(
            isRequestedVisible = true,
            source = source,
            pending = AudioBarPendingAction.Load(request),
            dialog = AudioBarDialog.None,
            preview = null,
            isTimingAvailable = false,
        )
        if (ensureBound()) performPendingAction()
    }

    private fun performPendingAction() {
        val connected = service ?: return
        when (val pending = session.pending) {
            is AudioBarPendingAction.Load -> {
                val request = pending.request
                session = session.copy(
                    pending = null,
                    playback = session.playback.copy(
                        versionId = request.versionId, audioId = request.audioId,
                        bookId = request.bookId, chapter_1 = request.chapter_1,
                        verse_1 = 0, positionMs = 0L, durationMs = 0L,
                        isPlaying = false, preparing = true, error = null, logs = emptyList(),
                    ),
                    preview = null,
                    isTimingAvailable = false,
                )
                connected.loadChapter(request)
            }
            is AudioBarPendingAction.FollowReader -> {
                if (!pending.isAutoPlay) connected.pause()
                val source = session.source ?: sourceFromPlayback(session.playback)
                if (source == null) {
                    closeSession(isStopPlayback = true)
                } else {
                    queueLoad(source, pending.target, 0, pending.isAutoPlay)
                }
            }
            AudioBarPendingAction.Restore, null -> Unit
        }
    }

    private fun receivePlayback(connected: BibleAudioService, snapshot: PlaybackState) {
        if (connected !== service || !session.isRequestedVisible || snapshot != connected.playbackState.value) return
        when (session.pending) {
            is AudioBarPendingAction.Load -> {
                performPendingAction()
                return
            }
            is AudioBarPendingAction.FollowReader -> {
                session = session.copy(playback = snapshot)
                performPendingAction()
                return
            }
            AudioBarPendingAction.Restore -> session = session.copy(pending = null)
            null -> Unit
        }
        if (!snapshot.isActive) {
            closeSession(isStopPlayback = false)
            return
        }
        val currentHost = host ?: return
        if (snapshot.versionId !in currentHost.audioVisibleVersionIds()) {
            closeSession(isStopPlayback = false)
            return
        }
        val previous = session.playback
        val isSameRecordingChapter = previous.versionId == snapshot.versionId && previous.audioId == snapshot.audioId &&
            previous.bookId == snapshot.bookId && previous.chapter_1 == snapshot.chapter_1
        val source = sourceFromPlayback(snapshot)
        session = session.copy(
            playback = snapshot,
            source = source,
            preview = if (isSameRecordingChapter) session.preview else null,
            isTimingAvailable = computeTimingAvailable(
                setHasTiming = source?.let(::resolvedSet)?.hasTiming,
                stateVerse1 = snapshot.verse_1,
                currentTimingAvailable = isSameRecordingChapter && session.isTimingAvailable,
            ),
        )
        if (previous.bookId != snapshot.bookId || previous.chapter_1 != snapshot.chapter_1) {
            val targetBook = currentHost.audioVisibleVersionIds().firstNotNullOfOrNull {
                currentHost.audioBookInVersion(it, snapshot.bookId)
            }
            if (targetBook != null && (currentHost.audioCurrentBook().bookId != snapshot.bookId ||
                    currentHost.audioCurrentChapter1() != snapshot.chapter_1)) {
                currentHost.audioDisplayChapter(targetBook, snapshot.chapter_1)
            }
        }
    }

    private fun sourceFromPlayback(snapshot: PlaybackState): AudioSourceOption? {
        if (!snapshot.isActive) return null
        val currentHost = host ?: return null
        if (snapshot.versionId !in currentHost.audioVisibleVersionIds()) return null
        val versionName = currentHost.audioVersionShortName(snapshot.versionId) ?: return null
        val set = AudioSetsRepository.cachedSetsFor(snapshot.versionId)?.sets?.firstOrNull { it.audioId == snapshot.audioId }
        val previous = session.source?.takeIf { it.versionId == snapshot.versionId && it.audioId == snapshot.audioId }
        return AudioSourceOption(snapshot.versionId, versionName, snapshot.audioId, set?.title ?: previous?.title ?: snapshot.audioId)
    }

    fun refreshSetChoices() = transition { refreshChoices() }

    private fun refreshChoices() {
        val currentHost = host ?: return
        if (session.pending == null && session.playback.isActive) {
            session = session.copy(source = sourceFromPlayback(session.playback))
        }
        when (val dialog = session.dialog) {
            is AudioBarDialog.Source -> if (currentHost.audioAvailableSources().isEmpty()) closeSession(isStopPlayback = false)
            is AudioBarDialog.Recording -> if (buildSetGroups(currentHost, dialog.isTimedOnly).isEmpty()) {
                if (session.source == null) closeSession(isStopPlayback = false)
                else session = session.copy(dialog = AudioBarDialog.None)
            }
            else -> Unit
        }
    }

    internal fun onCommand(cmd: AudioBarCommand) = transition {
        val currentHost = host ?: return@transition
        if (!session.isRequestedVisible) return@transition
        var loggedCommand = cmd
        when (cmd) {
            is AudioBarCommand.PickSource -> {
                val dialog = session.dialog as? AudioBarDialog.Source ?: return@transition
                val source = currentHost.audioAvailableSources().firstOrNull { it.versionId == cmd.versionId } ?: return@transition
                queueLoad(source, dialog.target, dialog.startVerse1, isAutoPlay = true)
            }
            AudioBarCommand.CancelPicker, AudioBarCommand.Close -> closeSession(isStopPlayback = true)
            AudioBarCommand.PlayPause -> {
                val connected = service ?: return@transition
                if (session.playback.isPlaying) connected.pause() else connected.play()
            }
            AudioBarCommand.PrevVerse -> service?.seekToPrevVerse()
            AudioBarCommand.NextVerse -> service?.seekToNextVerse()
            AudioBarCommand.Speed -> session = session.copy(dialog = AudioBarDialog.Speed)
            AudioBarCommand.DismissSpeedSheet, AudioBarCommand.DismissLogSheet -> session = session.copy(dialog = AudioBarDialog.None)
            AudioBarCommand.OpenSetSheet -> {
                val target = currentTarget() ?: return@transition
                if (buildSetGroups(currentHost, isTimedOnly = false).isEmpty()) return@transition
                session = session.copy(dialog = AudioBarDialog.Recording(target, 0, isTimedOnly = false))
            }
            AudioBarCommand.DismissSetSheet -> {
                if (session.source == null) closeSession(isStopPlayback = false)
                else session = session.copy(dialog = AudioBarDialog.None)
            }
            is AudioBarCommand.PickSet -> pickSet(currentHost, cmd.versionId, cmd.audioId)
            AudioBarCommand.Retry -> {
                val source = session.source ?: return@transition
                val target = currentTarget() ?: return@transition
                val request = buildRequest(source, target, 0, isAutoPlay = true) ?: return@transition
                AudioSetsRepository.invalidate(source.versionId)
                queueRequest(source, request)
            }
            is AudioBarCommand.SetSpeed -> {
                session = session.copy(playback = session.playback.copy(speed = cmd.speed), dialog = AudioBarDialog.None)
                service?.setSpeed(cmd.speed)
            }
            is AudioBarCommand.SeekDrag -> {
                val connected = service ?: return@transition
                session = session.copy(preview = AudioBarDragPreview(cmd.positionMs, connected.peekVerseAt(cmd.positionMs)))
            }
            is AudioBarCommand.SeekCommit -> {
                val preview = session.preview ?: return@transition
                session = session.copy(preview = null)
                loggedCommand = AudioBarCommand.SeekCommit(preview.positionMs)
                service?.seekTo(preview.positionMs)
            }
            AudioBarCommand.OpenLogSheet -> session = session.copy(dialog = AudioBarDialog.Log)
        }
        describeCommand(loggedCommand)?.let { service?.logUiEvent(it) }
    }

    private fun pickSet(currentHost: Host, versionId: String, audioId: String) {
        val dialog = session.dialog as? AudioBarDialog.Recording
        val target = dialog?.target ?: currentTarget() ?: return
        val set = AudioSetsRepository.cachedSetsFor(versionId)?.sets?.firstOrNull { it.audioId == audioId } ?: return
        if (!set.coversBook(target.bookId)) return
        val versionName = currentHost.audioVersionShortName(versionId) ?: return
        val source = AudioSourceOption(versionId, versionName, set.audioId, set.title)
        val pendingVerse = dialog?.startVerse1 ?: (session.pending as? AudioBarPendingAction.Load)?.request?.startVerse_1 ?: 0
        val startVerse = startVerseForSetSwitch(set.hasTiming, pendingVerse, session.playback.verse_1)
        AudioSetSelections.store(versionId, set.audioId)
        if (source.versionId == session.source?.versionId && source.audioId == session.source?.audioId &&
            session.pending == null && target.matches(session.playback) && service != null
        ) {
            session = session.copy(dialog = AudioBarDialog.None)
            if (pendingVerse > 0) {
                service?.seekToVerse(pendingVerse)
                service?.play()
            }
        } else {
            queueLoad(source, target, startVerse, isAutoPlay = true)
        }
    }

    private fun resolvedSet(source: AudioSourceOption): AudioSet? = AudioSetsRepository.cachedSetsFor(source.versionId)
        ?.sets?.firstOrNull { it.audioId == source.audioId }

    private fun selectedSet(): AudioSet? = session.source?.let(::resolvedSet)

    private fun buildSetGroups(currentHost: Host, isTimedOnly: Boolean): List<AudioSetGroup> = buildSetGroups(
        versionIds = currentHost.audioVisibleVersionIds().distinct(),
        currentBookId = currentHost.audioCurrentBook().bookId,
        selectedVersionId = session.source?.versionId,
        selectedAudioId = session.source?.audioId,
        timedOnly = isTimedOnly,
        setsOf = { AudioSetsRepository.cachedSetsFor(it)?.sets },
        versionNameOf = currentHost::audioVersionShortName,
        versionHasBook = { currentHost.audioBookInVersion(it, currentHost.audioCurrentBook().bookId) != null },
    )

    private fun displaySubtitle(source: AudioSourceOption): String {
        val sets = AudioSetsRepository.cachedSetsFor(source.versionId)?.sets
        return if (sets != null && sets.size > 1) "${source.shortName} · ${source.title}" else source.shortName
    }

    private fun transition(action: () -> Unit) {
        if (!scope.isActive) return
        transitions.addLast(action)
        if (isProcessingTransition) return
        isProcessingTransition = true
        try {
            while (transitions.isNotEmpty()) {
                transitions.removeFirst().invoke()
                publish()
            }
        } finally {
            isProcessingTransition = false
        }
    }

    private fun projectUiState(): AudioBarUiState {
        val playback = session.playback
        val dialog = session.dialog
        val currentHost = host
        val preview = session.preview
        return AudioBarUiState(
            visible = session.isRequestedVisible && session.source != null && dialog !is AudioBarDialog.Source,
            isPlaying = playback.isPlaying,
            preparing = playback.preparing || session.pending is AudioBarPendingAction.Load || session.pending is AudioBarPendingAction.FollowReader,
            positionMs = preview?.positionMs ?: playback.positionMs,
            durationMs = playback.durationMs,
            verse_1 = preview?.verse1 ?: playback.verse_1,
            speed = playback.speed,
            error = playback.error,
            timingAvailable = session.isTimingAvailable,
            logs = playback.logs,
            showLogSheet = dialog == AudioBarDialog.Log,
            playingVersionId = session.source?.versionId,
            canChooseSet = currentHost != null && canChooseSet(currentHost.audioVisibleVersionIds()) {
                AudioSetsRepository.cachedSetsFor(it)?.sets
            },
            pickerOptions = if (dialog is AudioBarDialog.Source) currentHost?.audioAvailableSources() else null,
            showSpeedSheet = dialog == AudioBarDialog.Speed,
            setGroups = if (dialog is AudioBarDialog.Recording && currentHost != null) buildSetGroups(currentHost, dialog.isTimedOnly) else null,
        )
    }

    private fun publish() {
        val previous = _uiState.value
        val next = projectUiState()
        _uiState.value = next
        if (next.visible || next.pickerOptions != null || next.setGroups != null || next.showSpeedSheet || next.showLogSheet) {
            ensureComposeContent()
        } else {
            teardownComposeContent()
        }
        if (previous.visible != next.visible || previous.pickerOptions != next.pickerOptions) {
            host?.audioBarVisibilityChanged(next.visible)
        }
        describeStateTransition(previous, next)?.let { service?.logUiEvent(it) }
    }

    private fun ensureComposeContent() {
        val view = composeView ?: return
        if (isComposeContentInstalled) return
        view.setContent {
            val state by uiState.collectAsState()
            AudioBar(state = state, onCommand = ::onCommand, modifier = Modifier)
        }
        isComposeContentInstalled = true
    }

    private fun teardownComposeContent() {
        if (!isComposeContentInstalled) return
        composeView?.disposeComposition()
        isComposeContentInstalled = false
    }

    companion object {
        private const val TAG = "AudioBarController"

        /**
         * Log event for a user interaction with the bar, or null for commands
         * not worth a line. [AudioBarCommand.SeekDrag] is excluded because it
         * fires continuously while the thumb moves and would bury every other
         * event in the log; the [AudioBarCommand.SeekCommit] that ends the
         * drag carries the same information. Pure for unit testing.
         */
        internal fun describeCommand(cmd: AudioBarCommand): AudioLogMessage? = when (cmd) {
            AudioBarCommand.PlayPause -> AudioLogMessage(R.string.audio_log_ui_play_pause)
            AudioBarCommand.Retry -> AudioLogMessage(R.string.audio_log_ui_retry)
            AudioBarCommand.PrevVerse -> AudioLogMessage(R.string.audio_log_ui_prev_verse)
            AudioBarCommand.NextVerse -> AudioLogMessage(R.string.audio_log_ui_next_verse)
            AudioBarCommand.Close -> AudioLogMessage(R.string.audio_log_ui_close)
            AudioBarCommand.Speed -> AudioLogMessage(R.string.audio_log_ui_open_speed)
            AudioBarCommand.DismissSpeedSheet -> AudioLogMessage(R.string.audio_log_ui_dismiss_speed)
            AudioBarCommand.OpenSetSheet -> AudioLogMessage(R.string.audio_log_ui_open_set)
            AudioBarCommand.DismissSetSheet -> AudioLogMessage(R.string.audio_log_ui_dismiss_set)
            AudioBarCommand.OpenLogSheet -> AudioLogMessage(R.string.audio_log_ui_open_log)
            AudioBarCommand.DismissLogSheet -> AudioLogMessage(R.string.audio_log_ui_dismiss_log)
            AudioBarCommand.CancelPicker -> AudioLogMessage(R.string.audio_log_ui_cancel_picker)
            is AudioBarCommand.SetSpeed -> AudioLogMessage(R.string.audio_log_ui_set_speed, listOf(cmd.speed.toString()))
            is AudioBarCommand.PickSet -> AudioLogMessage(R.string.audio_log_ui_pick_set, listOf(cmd.audioId, cmd.versionId))
            is AudioBarCommand.PickSource -> AudioLogMessage(R.string.audio_log_ui_pick_source, listOf(cmd.versionId))
            is AudioBarCommand.SeekCommit -> AudioLogMessage(R.string.audio_log_ui_seek, listOf(cmd.positionMs))
            is AudioBarCommand.SeekDrag -> null
        }

        /**
         * Log event for a bar state transition, or null when [previous] and
         * [current] describe the same user-visible state. Only the states the
         * bar renders differently are tracked, since position ticks change the
         * state object ten times a second and must not produce log lines.
         * Pure for unit testing.
         */
        internal fun describeStateTransition(previous: AudioBarUiState, current: AudioBarUiState): AudioLogMessage? {
            val from = uiStateLabelRes(previous)
            val to = uiStateLabelRes(current)
            return if (from == to) null else AudioLogMessage(R.string.audio_log_state_transition, listOf(from, to))
        }

        /**
         * Resource id naming the state, wrapped so the transition message can
         * embed it. The ids are compared for equality before being resolved,
         * which keeps the "did the state change" check free of any [Context].
         */
        private fun uiStateLabelRes(state: AudioBarUiState): AudioLogStateLabel = when {
            state.error != null -> AudioLogStateLabel(R.string.audio_log_state_error)
            state.preparing -> AudioLogStateLabel(R.string.audio_log_state_preparing)
            state.isPlaying -> AudioLogStateLabel(R.string.audio_log_state_playing)
            else -> AudioLogStateLabel(R.string.audio_log_state_paused)
        }

        /**
         * Whether verse highlight and verse-skip should be enabled. A selected
         * recording known to have no timing ([setHasTiming] = false) disables
         * them from the start, which the set list makes knowable before
         * playback, rather than leaving them to go inert once an empty timing
         * fetch returns. Otherwise (timing expected, or the set unknown on a
         * cold cache) they enable once the first non-zero verse hit arrives and
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
         * Whether the recording chooser has more than one recording to offer,
         * counted across every visible version. In split view a single
         * recording on each side is still a choice, since picking the other
         * side's moves audio across the splits. Pure for unit testing.
         */
        internal fun canChooseSet(
            versionIds: List<String>,
            setsOf: (versionId: String) -> List<AudioSet>?,
        ): Boolean = versionIds.distinct().sumOf { setsOf(it)?.size ?: 0 } > 1

        /**
         * Whether any listed recording can actually serve the book being read.
         * Applied to timing-filtered groups, this gates the "play audio from
         * this verse" verse action. Pure for unit testing.
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
