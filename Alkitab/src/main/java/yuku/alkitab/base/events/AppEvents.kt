package yuku.alkitab.base.events

import android.view.View
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * In-process event buses, replacing the LocalBroadcastManager-based signalling that the app
 * used to coordinate between activities, fragments, custom views, background threads, and the
 * sync worker. Each bus is a [MutableSharedFlow] with no replay and a small extra buffer.
 *
 * Overflow is [BufferOverflow.DROP_OLDEST]: if a burst of emissions fills the buffer before
 * the collector catches up, the oldest queued signal is discarded rather than the newest.
 * These are "reload" / "something changed" signals — the collector cares that *at least one*
 * change is pending, never about the exact count — so keeping the freshest event is preferred
 * and [MutableSharedFlow.tryEmit] is guaranteed to succeed from any thread without suspending.
 */
object AppEvents {
    private fun <T> bus(): MutableSharedFlow<T> = MutableSharedFlow(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Verse attribute map (markers / highlights / notes / progress marks) changed. */
    @JvmField
    val attributeMapChanged: MutableSharedFlow<Unit> = bus()

    /** The main active version swapped. */
    @JvmField
    val activeVersionChanged: MutableSharedFlow<Unit> = bus()

    /** Night mode toggled. */
    @JvmField
    val nightModeChanged: MutableSharedFlow<Unit> = bus()

    /** A display preference changed that requires the reader activity to be restarted. */
    @JvmField
    val needsRestart: MutableSharedFlow<Unit> = bus()

    /** The reading-plan "current reading" selection changed (set / cleared). */
    @JvmField
    val currentReadingChanged: MutableSharedFlow<Unit> = bus()

    /** Version list should be reloaded (item added/removed, download progress, reorder, etc.). */
    @JvmField
    val versionListReload: MutableSharedFlow<Unit> = bus()

    /** Version list pull-to-refresh spinner: true = refreshing, false = stop. */
    @JvmField
    val versionListRefreshingStatus: MutableSharedFlow<Boolean> = bus()

    /** Labels list (MarkersActivity) should be reloaded. */
    @JvmField
    val markersReload: MutableSharedFlow<Unit> = bus()

    /** Marker list (MarkerListActivity) should be reloaded. */
    @JvmField
    val markerListReload: MutableSharedFlow<Unit> = bus()

    /** Reading plan progress changed (typically after sync applies delta). */
    @JvmField
    val readingPlanProgressChanged: MutableSharedFlow<Unit> = bus()

    /** Sync settings screen should refresh (sync starting / stopping / status change). */
    @JvmField
    val syncSettingsReload: MutableSharedFlow<Unit> = bus()

    /** A devotion article finished downloading, carrying the kind name and yyyyMMdd date. */
    @JvmField
    val devotionDownloaded: MutableSharedFlow<DevotionDownloadedEvent> = bus()

    data class DevotionDownloadedEvent(@JvmField val name: String, @JvmField val date: String)

    // ---- Java interop emit helpers ------------------------------------------

    @JvmStatic fun emitAttributeMapChanged() { attributeMapChanged.tryEmit(Unit) }
    @JvmStatic fun emitActiveVersionChanged() { activeVersionChanged.tryEmit(Unit) }
    @JvmStatic fun emitNightModeChanged() { nightModeChanged.tryEmit(Unit) }
    @JvmStatic fun emitNeedsRestart() { needsRestart.tryEmit(Unit) }
    @JvmStatic fun emitCurrentReadingChanged() { currentReadingChanged.tryEmit(Unit) }
    @JvmStatic fun emitVersionListReload() { versionListReload.tryEmit(Unit) }
    @JvmStatic fun emitVersionListRefreshingStatus(refreshing: Boolean) {
        versionListRefreshingStatus.tryEmit(refreshing)
    }
    @JvmStatic fun emitMarkersReload() { markersReload.tryEmit(Unit) }
    @JvmStatic fun emitMarkerListReload() { markerListReload.tryEmit(Unit) }
    @JvmStatic fun emitReadingPlanProgressChanged() { readingPlanProgressChanged.tryEmit(Unit) }
    @JvmStatic fun emitSyncSettingsReload() { syncSettingsReload.tryEmit(Unit) }
    @JvmStatic fun emitDevotionDownloaded(name: String, date: String) {
        devotionDownloaded.tryEmit(DevotionDownloadedEvent(name, date))
    }

    // ---- Lifecycle-aware observation helpers (Java-friendly) --------------

    /**
     * Collect [flow] tied to [owner]'s [Lifecycle]. The collection starts when this is called
     * and ends when the lifecycle reaches [Lifecycle.State.DESTROYED]. Equivalent to registering
     * a BroadcastReceiver in onCreate and unregistering in onDestroy — receives events even
     * while the activity/fragment is stopped.
     */
    @JvmStatic
    fun observe(owner: LifecycleOwner, flow: Flow<*>, onEvent: Runnable): Job {
        return owner.lifecycleScope.launch {
            flow.collect { onEvent.run() }
        }
    }

    /**
     * Collect [flow] only while [owner] is at least [Lifecycle.State.STARTED] (visible);
     * pauses collection when stopped, resumes when started again, stops for good when destroyed.
     * Equivalent to registering a BroadcastReceiver in onStart and unregistering in onStop.
     */
    @JvmStatic
    fun observeWhileStarted(owner: LifecycleOwner, flow: Flow<*>, onEvent: Runnable): Job {
        return owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect { onEvent.run() }
            }
        }
    }

    /**
     * Typed variant of [observeWhileStarted] that delivers the emitted value.
     */
    @JvmStatic
    fun <T> observeWhileStartedWithValue(
        owner: LifecycleOwner,
        flow: Flow<T>,
        onEvent: Consumer<T>,
    ): Job {
        return owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect { onEvent.accept(it) }
            }
        }
    }

    // ---- Observation helper for custom Views (Java-friendly) ---------------

    /**
     * Collect [flow] on the main thread for as long as [view] is attached to a window.
     * Each attach starts a fresh collect job; each detach cancels it. Safe to call before
     * the view is ever attached (no coroutine runs until the first attach) and safe across
     * multiple attach/detach cycles (re-attach resubscribes).
     *
     * Should be called once per view instance (e.g. from the constructor or `onFinishInflate`);
     * calling it from `onAttachedToWindow` would accumulate a listener per attach cycle.
     */
    @JvmStatic
    fun observeOnView(view: View, flow: Flow<*>, onEvent: Runnable) {
        val listener = object : View.OnAttachStateChangeListener {
            private var job: Job? = null

            override fun onViewAttachedToWindow(v: View) {
                if (job?.isActive == true) return
                job = MainScope().launch { flow.collect { onEvent.run() } }
            }

            override fun onViewDetachedFromWindow(v: View) {
                job?.cancel()
                job = null
            }
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) {
            listener.onViewAttachedToWindow(view)
        }
    }
}
