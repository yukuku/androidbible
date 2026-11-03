package yuku.alkitab.base.audio

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import yuku.alkitab.base.widget.ConfigurationWrapper

internal const val AUDIO_LOG_LIMIT = 500

abstract class AudioPlayback(context: Context, protected val owner: AudioService) : ContextWrapper(context) {
    protected var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var diagnosticJob: Job? = null
    private var localizedContext: Context? = null
    private var localizedContextSerial = -1
    private var isStopping = false
    protected val isActive: Boolean get() = owner.isActive(this) && !isStopping
    abstract val isPlaybackOngoing: Boolean
    protected abstract fun releasePlayer()
    protected abstract fun resetPlayback()

    open fun onCreate() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    open fun onDestroy() {
        scope.cancel()
        releasePlayer()
    }

    fun stop() {
        if (!isActive) return
        isStopping = true
        try { resetPlayback() } finally { isStopping = false }
        owner.finish(this)
    }

    protected fun createMediaSession(player: Player, activity: PendingIntent, channelId: String, channelName: Int) {
        owner.installSession(player, activity, channelId, channelName)
    }

    protected fun cancelDiagnosticTimer() {
        diagnosticJob?.cancel()
        diagnosticJob = null
    }

    protected fun scheduleDiagnostic(isWaiting: () -> Boolean, onTimeout: () -> Unit) {
        if (diagnosticJob?.isActive == true) return
        diagnosticJob = scope.launch {
            delay(5_000L)
            if (isWaiting()) onTimeout()
        }
    }

    // Services resolve user-visible messages with the app language, independently of the device locale.
    protected fun localizedContext(): Context {
        val serial = ConfigurationWrapper.getSerialCounter()
        if (serial != localizedContextSerial || localizedContext == null) {
            localizedContext = ConfigurationWrapper.localizedContext(this)
            localizedContextSerial = serial
        }
        return localizedContext ?: this
    }

    protected fun logEntry(message: AudioLogMessage): AudioLogEntry =
        AudioLogEntry(System.currentTimeMillis(), message.resolve(localizedContext()))
}
