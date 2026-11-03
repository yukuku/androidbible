package yuku.alkitab.base.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import yuku.alkitab.base.widget.ConfigurationWrapper

// Repeated buffering can emit many HTTP events per byte-range fetch; cap retained log memory.
internal const val AUDIO_LOG_LIMIT = 500

@OptIn(UnstableApi::class)
abstract class BaseAudioService : MediaSessionService(), AudioPlaybackCoordinator.Session {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var diagnosticJob: Job? = null
    private var mediaSession: MediaSession? = null
    private var localizedContext: Context? = null
    private var localizedContextSerial = -1

    protected abstract val localBindAction: String
    protected abstract val serviceBinder: IBinder
    protected abstract val isAudioPlaying: Boolean
    protected abstract fun releasePlayer()
    abstract fun stop()

    protected fun createMediaSession(player: Player, sessionId: String, activity: PendingIntent, channelId: String, channelName: Int) {
        mediaSession = MediaSession.Builder(this, player)
            .setId(sessionId)
            .setSessionActivity(activity)
            .build()
            .also { addSession(it) }
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(channelId)
                .setChannelName(channelName)
                .build()
        )
    }

    final override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == localBindAction) serviceBinder else super.onBind(intent)

    final override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    final override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isAudioPlaying) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.release()
        mediaSession = null
        releasePlayer()
        AudioPlaybackCoordinator.release(this)
        super.onDestroy()
    }

    final override fun stopPlayback() = stop()

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
