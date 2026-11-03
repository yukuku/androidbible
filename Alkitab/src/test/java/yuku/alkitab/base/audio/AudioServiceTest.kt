package yuku.alkitab.base.audio

import android.content.Context
import android.content.Intent
import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import yuku.alkitab.songs.SongAudioPlayback

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class AudioServiceTest {
    private class Playback(context: Context, owner: AudioService, val events: MutableList<String>, val name: String) : AudioPlayback(context, owner) {
        override var isPlaybackOngoing = false
        val result = CompletableDeferred<Unit>()
        var isResultApplied = false
        var timeouts = 0
        override fun onCreate() {
            super.onCreate()
            events.add("create $name")
            scope.launch { result.await(); isResultApplied = true }
        }
        override fun resetPlayback() { events.add("stop $name") }
        override fun releasePlayer() { events.add("release $name") }
        fun schedule() = scheduleDiagnostic({ true }, { timeouts++ })
    }

    @Test
    fun `switching modes stops old playback and releases it after activating the new mode`() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        val service = controller.get()
        val events = mutableListOf<String>()
        val bible = Playback(service, service, events, "bible")
        val song = Playback(service, service, events, "song")
        service.activate(bible)
        bible.schedule()
        service.activate(song)
        assertEquals(listOf("create bible", "stop bible", "create song", "release bible"), events)
        assertTrue(service.isActive(song))
        assertFalse(service.isActive(bible))
        bible.stop()
        assertEquals(4, events.size)
        bible.result.complete(Unit)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertFalse(bible.isResultApplied)
        assertEquals(0, bible.timeouts)
        service.activate(song)
        assertEquals(4, events.size)
        service.activate(bible)
        assertTrue(service.isActive(bible))
        controller.destroy()
        assertEquals("release bible", events.last())
        assertFalse(service.isActive(bible))
    }

    @Test
    fun `player cleanup is safe before creation and releases each player only once`() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        val service = controller.get()
        val bible = mockk<BibleAudioPlayer>(relaxed = true)
        val song = mockk<androidx.media3.exoplayer.ExoPlayer>(relaxed = true)
        for ((playback, player) in listOf(service.bible to bible, service.song to song)) {
            assertFalse(playback.isPlaybackOngoing)
            playback.onDestroy()
            playback.javaClass.getDeclaredField("playerInstance").apply { isAccessible = true }.set(playback, player)
            playback.onDestroy()
            assertFalse(playback.isPlaybackOngoing)
            playback.onDestroy()
        }
        verify(exactly = 1) { bible.release() }
        verify(exactly = 1) { song.release() }
        controller.destroy()
    }

    @Test
    fun `both screens bind to the same service with stable feature endpoints`() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        val service = controller.get()
        val binder = service.onBind(Intent(AudioService.ACTION_LOCAL_BIND)) as AudioService.LocalBinder
        assertSame(service, binder.service)
        assertSame(service.bible, binder.service?.bible)
        assertSame(service.song, binder.service?.song)
        controller.destroy()
    }

    @Test
    fun `removing a task keeps requested buffering playback alive`() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        val service = controller.get()
        val playback = Playback(service, service, mutableListOf(), "buffering")
        service.activate(playback)
        playback.isPlaybackOngoing = true
        service.onTaskRemoved(null)
        assertFalse(shadowOf(service).isStoppedBySelf)
        playback.isPlaybackOngoing = false
        service.onTaskRemoved(null)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun `inactive feature controls cannot touch a player or stop the active mode`() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        val service = controller.get()
        val events = mutableListOf<String>()
        val active = Playback(service, service, events, "active")
        service.activate(active)
        service.bible.pause()
        service.bible.seekTo(500)
        service.bible.play()
        service.bible.stop()
        service.song.pause()
        service.song.seekTo(500)
        service.song.setLoop(true)
        service.song.play()
        service.song.stop()
        assertTrue(service.isActive(active))
        assertFalse(shadowOf(service).isStoppedBySelf)
        assertEquals(listOf("create active"), events)
        controller.destroy()
    }

    @Test
    fun `real mode switches retain one session and reset format speed repeat and notification destination`() {
        io.mockk.mockkStatic(yuku.alkitab.base.connection.Connections::class)
        every { yuku.alkitab.base.connection.Connections.httpUserAgent } returns "test"
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(503).message("Offline").body("offline".toResponseBody()).build()
        }.build()
        every { yuku.alkitab.base.connection.Connections.okHttp } returns client
        val controller = Robolectric.buildService(AudioService::class.java).create()
        val service = controller.get()
        try {
            service.song.load(SongAudioPlayback.SongRequest("https://example.invalid/song.mid", "Song", "Book", true))
            service.onTaskRemoved(null)
            assertFalse(shadowOf(service).isStoppedBySelf)
            val session = requireNotNull(service.onGetSession(mockk()))
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, session.player.repeatMode)
            assertEquals(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC, session.player.audioAttributes.contentType)
            assertEquals(yuku.alkitab.songs.SongViewActivity::class.java.name,
                shadowOf(requireNotNull(session.sessionActivity)).savedIntent.component?.className)

            service.activate(service.bible)
            assertSame(session, service.onGetSession(mockk()))
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, session.player.repeatMode)
            assertEquals(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH, session.player.audioAttributes.contentType)
            assertTrue(session.player.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            assertEquals(yuku.alkitab.base.IsiActivity::class.java.name,
                shadowOf(requireNotNull(session.sessionActivity)).savedIntent.component?.className)
            service.bible.setSpeed(1.5f)

            service.song.load(SongAudioPlayback.SongRequest("https://example.invalid/song.mp3", "Song", "Book", false))
            assertSame(session, service.onGetSession(mockk()))
            assertEquals(1f, session.player.playbackParameters.speed)
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, session.player.repeatMode)
            assertFalse(session.player.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            service.bible.stop()
            assertTrue(service.song.playbackState.value.hasMedia)
        } finally {
            controller.destroy()
            io.mockk.unmockkStatic(yuku.alkitab.base.connection.Connections::class)
        }
    }

    @Test
    fun `session switches player and notification activity before outgoing player release`() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        val service = controller.get()
        val session = mockk<androidx.media3.session.MediaSession>(relaxed = true)
        AudioService::class.java.getDeclaredField("session").apply { isAccessible = true }.set(service, session)
        val player = mockk<androidx.media3.common.Player>(relaxed = true)
        val activity = android.app.PendingIntent.getActivity(service, 0, Intent(), android.app.PendingIntent.FLAG_IMMUTABLE)
        service.installSession(player, activity, "audio_song", yuku.alkitab.debug.R.string.audio_song_notification_channel_name)
        verify { session.setPlayer(player) }
        verify { session.setSessionActivity(activity) }
        assertSame(session, service.onGetSession(mockk()))
        controller.destroy()
        verify(exactly = 1) { session.release() }
    }
}
