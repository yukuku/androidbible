package yuku.alkitab.songs

import android.app.Activity
import android.content.ServiceConnection
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import yuku.alkitab.base.audio.testSongPlayback

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class SongAudioDiagnosticsTest {
    private lateinit var service: SongAudioPlayback
    private lateinit var player: ExoPlayer
    private lateinit var listener: Player.Listener
    private var isPlayWhenReady = false
    private var isPlaying = false
    private val request = SongAudioPlayback.SongRequest("https://example.com/song.mid", "KJ 1", "Song", false)

    @Before
    fun setUp() {
        service = testSongPlayback()
        player = mockk(relaxed = true)
        every { player.playWhenReady } answers { isPlayWhenReady }
        every { player.playWhenReady = any() } answers { isPlayWhenReady = firstArg() }
        every { player.isPlaying } answers { isPlaying }
        every { player.stop() } answers { setPlaying(false) }
        field("playerInstance").set(service, player)
        listener = field("playerListener").get(service) as Player.Listener
    }

    @After
    fun tearDown() {
        service.stop()
    }

    @Test
    fun `a song that has not started shows diagnostics after exactly five seconds`() {
        service.load(request)
        advance(4_999)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
        advance(1)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
        assertTrue(service.playbackState.value.logs.isNotEmpty())
    }

    @Test
    fun `successful playback before the deadline keeps diagnostics hidden`() {
        service.load(request)
        advance(4_000)
        setPlaying(true)
        advance(5_000)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `a quick failure still exposes its diagnostic log at the deadline`() {
        service.load(request)
        advance(1_000)
        listener.onPlayerError(PlaybackException("Failed to fetch", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        advance(3_999)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
        advance(1)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
        assertEquals("IO_NETWORK_CONNECTION_FAILED", service.playbackState.value.error)
        assertTrue(service.playbackState.value.logs.last().message.contains("Failed to fetch"))
        assertFalse(service.playbackState.value.logs.last().message.contains("ERROR_CODE_"))
    }

    @Test
    fun `diagnostics remain visible through playback pause retry and another clip of the same song`() {
        service.load(request)
        advance(5_000)
        setPlaying(true)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
        service.pause()
        setPlaying(false)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
        service.load(request)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
        service.load(request.copy(url = "https://example.com/song.mp3"))
        assertTrue(service.playbackState.value.isDiagnosticVisible)
        assertEquals(1, service.playbackState.value.logs.size)
    }

    @Test
    fun `pausing a pending attempt cancels the diagnostic deadline`() {
        service.load(request)
        advance(4_000)
        service.pause()
        advance(5_000)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
        service.play()
        advance(4_999)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
        advance(1)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `buffering after successful playback gets its own five second deadline`() {
        service.load(request)
        setPlaying(true)
        advance(6_000)
        setPlaying(false)
        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        advance(4_999)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
        advance(1)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `a stalled seek starts diagnostics even before the playing state callback arrives`() {
        service.load(request)
        setPlaying(true)
        every { player.duration } returns 120_000L
        every { player.seekTo(90_000L) } answers { isPlaying = false }

        service.seekTo(90_000L)
        assertTrue(service.playbackState.value.isPlaying)
        advance(4_999)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
        advance(1)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `a seek that resumes playback before the deadline leaves diagnostics hidden`() {
        service.load(request)
        setPlaying(true)
        every { player.duration } returns 120_000L
        every { player.seekTo(90_000L) } answers { isPlaying = false }
        service.seekTo(90_000L)
        advance(4_000)
        isPlaying = true
        advance(1_000)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `stopping clears diagnostics and cancels any pending deadline`() {
        service.load(request)
        advance(4_000)
        service.stop()
        advance(6_000)
        assertEquals(SongPlaybackState.IDLE, service.playbackState.value)
        service.load(request)
        advance(5_000)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
        service.stop()
        assertEquals(SongPlaybackState.IDLE, service.playbackState.value)
        service.load(request)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `replacing the activity preserves the same song diagnostics and selecting another song clears them`() {
        val controller = SongAudioController(mockk(relaxed = true))
        controller.selectSong("KJ", "1")
        controller.mediaKnownToExist(request.url)
        val connection = SongAudioController::class.java.getDeclaredField("connection").apply { isAccessible = true }
            .get(controller) as ServiceConnection
        val owner = mockk<yuku.alkitab.base.audio.AudioService>(relaxed = true)
        every { owner.song } returns service
        connection.onServiceConnected(null, yuku.alkitab.base.audio.AudioService.LocalBinder(owner))
        val firstActivity = Robolectric.buildActivity(Activity::class.java).create()
        controller.setUI(firstActivity.get(), mockk(relaxed = true))
        service.load(request)
        firstActivity.destroy()
        advance(5_000)

        val secondActivity = Robolectric.buildActivity(Activity::class.java).create()
        try {
            controller.setUI(secondActivity.get(), mockk(relaxed = true))
            controller.selectSong("KJ", "1")
            assertTrue(controller.playbackState.value.isDiagnosticVisible)
            assertEquals(service.playbackState.value.logs, controller.playbackState.value.logs)
            verify(exactly = 0) { player.stop() }

            controller.selectSong("KJ", "2")
            assertEquals(SongPlaybackState.IDLE, controller.playbackState.value)
            assertEquals(SongPlaybackState.IDLE, service.playbackState.value)
        } finally {
            secondActivity.destroy()
            connection.onServiceDisconnected(null)
        }
    }

    private fun setPlaying(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        listener.onIsPlayingChanged(isPlaying)
    }

    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun field(name: String) = SongAudioPlayback::class.java.getDeclaredField(name).apply { isAccessible = true }
}
