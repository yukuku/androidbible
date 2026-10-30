package yuku.alkitab.songs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
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
 * Maps the service's [SongPlaybackState] onto [MediaController.State] for the
 * song media sheet and activity. Format selection (MP3 or MIDI) belongs to the
 * service.
 */
class SongAudioController(private val appContext: Context) : MediaController() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var service: SongAudioService? = null
    private var bound = false

    var isLooping by mutableStateOf(false)
        private set

    internal var playbackPlayer by mutableStateOf<Player?>(null)
        private set

    private var displayTitle = ""
    private var displaySubtitle = ""
    private var pendingRequest: SongAudioService.SongRequest? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? SongAudioService.LocalBinder)?.service ?: return
            service = svc
            playbackPlayer = svc.playbackPlayer
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
            playbackPlayer = null
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
        isLooping = false
        service?.stop()
        unbind()
    }

    override fun playOrPause(playInLoop: Boolean) {
        isLooping = playInLoop
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
                service?.pause()
            }

            State.paused -> {
                service?.setLoop(playInLoop)
                service?.play()
            }
        }
    }

    fun isCurrentUrl(candidate: String): Boolean = url == candidate && state != State.reset

    fun pause() {
        pendingRequest = null
        service?.pause()
    }

    fun seekTo(positionMs: Long) {
        service?.seekTo(positionMs)
    }

    fun setLoop(isLooping: Boolean) {
        this.isLooping = isLooping
        service?.setLoop(isLooping)
        pendingRequest = pendingRequest?.copy(loop = isLooping)
    }

    override fun getProgress(): LongArray {
        val svc = service ?: return longArrayOf(-1, -1)
        return when (state) {
            State.preparing, State.playing, State.paused, State.complete -> longArrayOf(svc.currentPositionMs, svc.durationMs)
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
        playbackPlayer = null
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
