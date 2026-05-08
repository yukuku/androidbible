package yuku.alkitab.base.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
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
import yuku.alkitab.base.audio.ui.AudioBar
import yuku.alkitab.base.audio.ui.AudioBarCommand
import yuku.alkitab.base.audio.ui.AudioBarUiState
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

        /** Version id of the side that drives audio (in M3, always primary). */
        fun audioCurrentVersionId(): String

        /** Short name of the version driving audio (e.g. "TB"). */
        fun audioCurrentVersionShortName(): String

        /**
         * The list of version ids currently visible in `IsiActivity` (primary,
         * plus the split-view secondary if open). Drives toolbar-icon visibility.
         */
        fun audioVisibleVersionIds(): List<String>

        /**
         * Resolve the chapter that's [direction] away (`-1` previous, `+1`
         * next). Returns `null` at Bible boundaries; the controller uses that
         * to render the chapter-nav button label as `alpha = 0f` so the bar
         * doesn't reflow.
         */
        fun audioNeighborChapter(direction: Int): Pair<Book, Int>?

        /**
         * Resolve a [Book] by id within the version currently driving audio.
         * Returns `null` if the version doesn't have that book, in which case
         * the controller silently skips the activity-side navigation.
         * Used when the service drives a chapter change (lock-screen prev/
         * next, Bluetooth) and the activity needs to follow.
         */
        fun audioVersionBook(bookId: Int): Book?

        /** Tell the activity to navigate to [book] / [chapter_1] (the existing `display` flow). */
        fun audioDisplayChapter(book: Book, chapter_1: Int)

        /** Notifies the activity that the spinner-vs-icon state may need to flip. */
        fun audioPreparingChanged(preparing: Boolean)
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
     * Set while the user has the slider thumb under their finger. Drives a
     * special-case in [projectToUi]: live playback continues to push a
     * `verse_1` derived from the player's current position every 100 ms, but
     * we don't want that to clobber the drag-preview verse_1 we just wrote
     * from [AudioBarCommand.SeekDrag]. Cleared on [AudioBarCommand.SeekCommit].
     */
    private var dragging = false

    private val _uiState = MutableStateFlow(AudioBarUiState.HIDDEN)
    val uiState: StateFlow<AudioBarUiState> = _uiState.asStateFlow()

    /**
     * Whether the toolbar audio icon should be shown for the currently visible
     * version(s). Reads the catalog synchronously (cached after the first call;
     * see [AudioCatalogRepository]).
     */
    val isAvailable: Boolean
        get() {
            val ids = host?.audioVisibleVersionIds() ?: return false
            return ids.any { AudioCatalogRepository.isAudioAvailable(it) }
        }

    /** True while the service is preparing a chapter — drives the toolbar spinner. */
    val isPreparing: Boolean
        get() = _uiState.value.preparing

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? BibleAudioService.LocalBinder ?: return
            val svc = localBinder.service
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
     * The compose host is `setContent`-ed eagerly so the bar can animate in
     * the moment [requestedVisible] flips, even before the service has
     * connected.
     */
    fun attach(host: Host, composeView: ComposeView) {
        this.host = host
        this.composeView = composeView
        composeView.setContent {
            val state by uiState.collectAsState()
            AudioBar(state = state, onCommand = ::onCommand, modifier = Modifier)
        }
    }

    /**
     * Tell `IsiActivity` to refresh whatever it derives from `isAvailable` —
     * specifically, the toolbar menu visibility. Called by the activity when
     * the active version changes. Cheap; just nudges the view-state flow so
     * recomposition picks up new chapter labels too.
     */
    fun onActiveVersionChanged() {
        host?.let { recomputeChapterLabels(it) }
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

    fun show() {
        val host = this.host ?: return
        requestedVisible = true
        ensureBound()
        // Service may not be connected yet; in that case the loadChapter call
        // below is queued via service.let, and we'll fire it when the binder
        // arrives. Either way, mark the UI visible immediately.
        _uiState.update { it.copy(visible = true, preparing = service == null) }
        host.audioPreparingChanged(true)
        service?.let { svc -> svc.loadChapter(buildRequest(host)) }
            ?: run {
                pendingLoad = host
            }
    }

    fun hide() {
        requestedVisible = false
        dragging = false
        service?.stop()
        _uiState.update { AudioBarUiState.HIDDEN }
        host?.audioPreparingChanged(false)
        // We deliberately keep the binding alive until detach(); rebinding is
        // cheap, but avoiding bind/unbind churn each time the user reopens the
        // bar matches what Spotify-style audio UIs do.
    }

    /**
     * Releases activity references. Called from `IsiActivity.onDestroy`.
     * Does NOT call `service.stop()` — the service is independently owned and
     * may continue playing when the activity is recreated (M4 lock-screen).
     */
    fun detach() {
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
        // media3's MediaSessionService transitions to foreground (posting the
        // lock-screen notification, allowing background playback) only via
        // `onStartCommand`. A pure `bindService` keeps the service alive while
        // the activity holds it but never promotes it — meaning audio would
        // stop the moment the screen locks. Calling `startForegroundService`
        // alongside `bindService` is the standard hybrid-pattern fix; the
        // service must `startForeground` within 5 seconds, which media3's
        // notification manager handles automatically the first time the
        // player updates (immediately for our case since `loadChapter` is
        // called right after this returns).
        val startIntent = Intent(context, BibleAudioService::class.java)
        ContextCompat.startForegroundService(context, startIntent)
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

    private fun buildRequest(host: Host): BibleAudioService.AudioRequest {
        val book = host.audioCurrentBook()
        val chapter1 = host.audioCurrentChapter1()
        val versionShort = host.audioCurrentVersionShortName()
        return BibleAudioService.AudioRequest(
            versionId = host.audioCurrentVersionId(),
            bookId = book.bookId,
            chapter_1 = chapter1,
            displayTitle = "${book.shortName} $chapter1",
            displaySubtitle = versionShort,
        )
    }

    private fun onCommand(cmd: AudioBarCommand) {
        val svc = service
        val host = this.host ?: return
        when (cmd) {
            AudioBarCommand.PlayPause -> {
                if (svc == null) return
                if (_uiState.value.isPlaying) svc.pause() else svc.play()
            }
            AudioBarCommand.PrevVerse -> svc?.seekToPrevVerse()
            AudioBarCommand.NextVerse -> svc?.seekToNextVerse()
            // Both in-app and lock-screen chapter skips fan in to the same
            // service entry point. The service is the single source of truth
            // for "what chapter is loaded"; AudioBarController.projectToUi
            // observes the resulting state change and pushes IsiActivity to
            // navigate so the on-screen chapter follows the audio.
            AudioBarCommand.PrevChapter -> svc?.skipChapter(-1)
            AudioBarCommand.NextChapter -> svc?.skipChapter(1)
            AudioBarCommand.Close -> hide()
            AudioBarCommand.Speed -> {
                // M5: opens the speed bottom sheet. M3 surfaces the chip but
                // ignores the tap.
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
        }
    }

    private fun projectToUi(state: PlaybackState) {
        val host = this.host
        val prevPreparing = _uiState.value.preparing
        // Snapshot before we drain — if a load was queued before the service
        // connected, the first incoming state is usually `IDLE`, which would
        // briefly clear the spinner before our loadChapter call sets it back
        // to preparing. Holding the spinner true until we've fired the
        // queued load avoids that flicker.
        val isPending = pendingLoad != null
        pendingLoad?.let { ph ->
            service?.loadChapter(buildRequest(ph))
            pendingLoad = null
        }

        // Sync the activity to the chapter the service is now playing. This
        // is what makes lock-screen / Bluetooth-headset prev/next-chapter
        // navigation feel right: when the user comes back to the app, the
        // reader is already on the chapter they were listening to. We compare
        // against host's current state and only navigate on a real difference,
        // so the in-app "tap prev-chapter on the audio bar" path (which is
        // already activity-driven via the service) stays a no-op here.
        if (host != null && state.bookId >= 0) {
            val hostBook = host.audioCurrentBook()
            if (hostBook.bookId != state.bookId || host.audioCurrentChapter1() != state.chapter_1) {
                val targetBook = host.audioVersionBook(state.bookId)
                if (targetBook != null) {
                    host.audioDisplayChapter(targetBook, state.chapter_1)
                }
            }
        }

        val prevLabel = host?.audioNeighborChapter(-1)?.let { (b, c) -> "${b.shortName} $c" }
        val nextLabel = host?.audioNeighborChapter(+1)?.let { (b, c) -> "${b.shortName} $c" }

        val effectivePreparing = state.preparing || isPending
        _uiState.update { current ->
            current.copy(
                visible = requestedVisible,
                isPlaying = state.isPlaying,
                preparing = effectivePreparing,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                // Keep our drag-preview verse_1 while the user is dragging;
                // otherwise let the live playback verse drive the highlight.
                verse_1 = if (dragging) current.verse_1 else state.verse_1,
                speed = state.speed,
                prevChapterLabel = prevLabel,
                nextChapterLabel = nextLabel,
                error = state.error,
                // M3 has no first-class signal for this; infer from "we got
                // a non-zero verse hit" once the service has had a chance to
                // process timing. Until then the prev/next-verse buttons stay
                // disabled, which is the spec.
                timingAvailable = state.verse_1 > 0 || current.timingAvailable,
            )
        }

        if (prevPreparing != effectivePreparing) {
            host?.audioPreparingChanged(effectivePreparing)
        }
    }

    private fun recomputeChapterLabels(host: Host) {
        val prev = host.audioNeighborChapter(-1)?.let { (b, c) -> "${b.shortName} $c" }
        val next = host.audioNeighborChapter(+1)?.let { (b, c) -> "${b.shortName} $c" }
        _uiState.update { it.copy(prevChapterLabel = prev, nextChapterLabel = next) }
    }

    companion object {
        private const val TAG = "AudioBarController"
    }
}
