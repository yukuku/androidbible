package yuku.alkitab.base.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.lang.ref.WeakReference
import yuku.alkitab.songs.SongAudioPlayback

@OptIn(UnstableApi::class)
class AudioService : MediaSessionService() {
    companion object {
        const val ACTION_LOCAL_BIND = "yuku.alkitab.audio.LOCAL_BIND"
    }

    // Binder JNI references can outlive unbinding; a weak reference avoids retaining the service.
    class LocalBinder(service: AudioService) : Binder() {
        private val reference = WeakReference(service)
        val service: AudioService? get() = reference.get()
    }

    val bible by lazy { BibleAudioPlayback(this, this) }
    val song by lazy { SongAudioPlayback(this, this) }
    private val binder = LocalBinder(this)
    private var active: AudioPlayback? = null
    private var session: MediaSession? = null
    private var isSwitching = false

    internal fun isActive(playback: AudioPlayback): Boolean = active === playback

    internal fun activate(playback: AudioPlayback) {
        if (active === playback) return
        val previous = active
        isSwitching = true
        try {
            previous?.stop()
            active = playback
            playback.onCreate()
        } catch (failure: RuntimeException) {
            active = null
            session?.release()
            session = null
            playback.onDestroy()
            stopSelf()
            throw failure
        } finally {
            // The session must detach the outgoing player before it is released.
            previous?.onDestroy()
            isSwitching = false
        }
    }

    internal fun finish(playback: AudioPlayback) {
        if (!isSwitching && active === playback) stopSelf()
    }

    internal fun installSession(player: Player, activity: PendingIntent, channelId: String, channelName: Int) {
        val current = session
        if (current == null) {
            session = MediaSession.Builder(this, player)
                .setId("audio")
                .setSessionActivity(activity)
                .build().also { addSession(it) }
        } else {
            current.setPlayer(player)
            current.setSessionActivity(activity)
        }
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(channelId)
                .setChannelName(channelName)
                .build()
        )
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_LOCAL_BIND) binder else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (active?.isPlaybackOngoing != true) stopSelf()
    }

    override fun onDestroy() {
        val previous = active
        active = null
        session?.release()
        session = null
        previous?.onDestroy()
        super.onDestroy()
    }
}
