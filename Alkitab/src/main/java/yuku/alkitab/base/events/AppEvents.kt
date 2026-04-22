package yuku.alkitab.base.events

import android.view.View
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * In-process event buses, replacing the LocalBroadcastManager-based signalling that the app
 * used to coordinate between activities, fragments, custom views, background threads, and the
 * sync worker. Each bus is a [MutableSharedFlow] with no replay and a small extra buffer so
 * [MutableSharedFlow.tryEmit] never drops events under normal load and can be called safely
 * from any thread without suspending (matching LBM's fire-and-forget semantics).
 */
object AppEvents {
    private fun <T> bus(): MutableSharedFlow<T> = MutableSharedFlow(extraBufferCapacity = 16)

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
     * Collect [flow] on the main thread until [view] is detached from its window. [onEvent]
     * receives each emitted value. Safe to call before the view is attached (the listener
     * starts immediately; the job is cancelled on the first detach).
     *
     * This matches the legacy `registerReceiver` on LBM followed by `unregisterReceiver` in
     * `onDetachedFromWindow`: a single observation window bounded by the view's detach.
     */
    @JvmStatic
    fun observeOnView(view: View, flow: Flow<*>, onEvent: Runnable): Job {
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val job = scope.launch {
            flow.collect { onEvent.run() }
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                scope.cancel()
                v.removeOnAttachStateChangeListener(this)
            }
        }
        view.addOnAttachStateChangeListener(listener)
        return job
    }
}
