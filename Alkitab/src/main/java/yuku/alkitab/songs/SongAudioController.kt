package yuku.alkitab.songs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import yuku.alkitab.base.util.AppLog

private const val TAG = "SongAudioController"

/**
 * Activity-facing [MediaController] that drives playback through the background
 * [SongAudioService]. Binds with `startService` + [SongAudioService.ACTION_LOCAL_BIND]
 * using the application context, so the service outlives the activity and keeps
 * playing in the background with its media notification.
 *
 * Maps the service's [SongPlaybackState] onto the [MediaController.State] machine
 * that [SongViewActivity]'s toolbar already understands, so the UI code is
 * unchanged. Replaces the former activity-scoped `ExoplayerController` and
 * `MidiController`; format selection (MP3 vs MIDI) is handled inside the service.
 */
class SongAudioController(private val appContext: Context) : MediaController() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var service: SongAudioService? = null
    private var bound = false

    private var displayTitle = ""
    private var displaySubtitle = ""
    private var pendingRequest: SongAudioService.SongRequest? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? SongAudioService.LocalBinder)?.service ?: return
            service = svc
            collectJob?.cancel()
            collectJob = scope.launch {
                svc.playbackState.collect { mapState(it) }
            }
            pendingRequest?.let { req ->
                svc.load(req)
                pendingRequest = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            collectJob?.cancel()
            collectJob = null
        }
    }

    /** Title / subtitle shown on the media notification for the next [load]. */
    fun setDisplayInfo(title: String, subtitle: String) {
        displayTitle = title
        displaySubtitle = subtitle
    }

    override fun reset() {
        super.reset()
        service?.stop()
        unbind()
    }

    override fun playOrPause(playInLoop: Boolean) {
        when (state) {
            State.reset -> Unit

            State.reset_media_known_to_exist, State.complete, State.error -> {
                val url = url ?: return
                state = State.preparing
                ensureBound()
                val request = SongAudioService.SongRequest(
                    url = url,
                    displayTitle = displayTitle,
                    displaySubtitle = displaySubtitle,
                    loop = playInLoop,
                )
                service?.load(request) ?: run { pendingRequest = request }
            }

            State.preparing -> Unit

            State.playing -> {
                if (playInLoop) {
                    service?.setLoop(true)
                } else {
                    service?.pause()
                }
            }

            State.paused -> {
                service?.setLoop(playInLoop)
                service?.play()
            }
        }
    }

    override fun getProgress(): LongArray {
        val svc = service ?: return longArrayOf(-1, -1)
        return when (state) {
            State.playing, State.paused, State.complete -> longArrayOf(svc.currentPositionMs, svc.durationMs)
            else -> longArrayOf(-1, -1)
        }
    }

    private fun mapState(s: SongPlaybackState) {
        state = when {
            s.error != null -> State.error
            s.preparing -> State.preparing
            s.isPlaying -> State.playing
            s.ended -> State.complete
            s.hasMedia -> State.paused
            else -> State.reset_media_known_to_exist
        }
    }

    private fun ensureBound() {
        if (bound) return
        val startIntent = Intent(appContext, SongAudioService::class.java)
        appContext.startService(startIntent)
        val bindIntent = Intent(appContext, SongAudioService::class.java)
            .setAction(SongAudioService.ACTION_LOCAL_BIND)
        try {
            if (appContext.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)) {
                bound = true
            } else {
                AppLog.w(TAG, "bindService returned false — service not bound")
            }
        } catch (e: SecurityException) {
            AppLog.e(TAG, "bindService denied: ${e.message}")
        }
    }

    private fun unbind() {
        if (bound) {
            try {
                appContext.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "unbindService: ${e.message}")
            }
            bound = false
        }
        service = null
        pendingRequest = null
        collectJob?.cancel()
        collectJob = null
    }
}
