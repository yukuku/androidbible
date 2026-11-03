package yuku.alkitab.base.audio

import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import yuku.alkitab.base.audio.model.VerseTiming

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class BibleAudioDiagnosticsTest {
    private lateinit var service: BibleAudioService
    private lateinit var player: BibleAudioPlayer
    private lateinit var listener: BibleAudioPlayer.Listener
    private var isWaitingForPlayback = false

    @Before
    fun setUp() {
        service = Robolectric.buildService(BibleAudioService::class.java).get()
        player = mockk(relaxed = true)
        every { player.isWaitingForPlayback } answers { isWaitingForPlayback }
        every { player.pause() } answers { isWaitingForPlayback = false }
        field("player").set(service, player)
        listener = field("playerListener").get(service) as BibleAudioPlayer.Listener
        @Suppress("UNCHECKED_CAST")
        val state = field("_playbackState").get(service) as MutableStateFlow<PlaybackState>
        state.value = PlaybackState.IDLE.copy(bookId = 0, chapter_1 = 1, isPlaying = true)
    }

    @After
    fun tearDown() {
        service.stop()
        AudioPlaybackCoordinator.resetForTest()
    }

    @Test
    fun `an offline seek shows diagnostics after five seconds even without a buffering callback`() {
        every { player.seekTo(90_000L) } answers { isWaitingForPlayback = true }
        service.seekTo(90_000L)
        assertFalse(service.playbackState.value.preparing)
        advance(4_999)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
        advance(1)
        assertTrue(service.playbackState.value.isDiagnosticVisible)

        val controller = AudioBarController(mockk(relaxed = true))
        AudioBarController::class.java.getDeclaredMethod("projectToUi", PlaybackState::class.java)
            .apply { isAccessible = true }.invoke(controller, service.playbackState.value)
        assertTrue(controller.uiState.value.isDiagnosticVisible)
    }

    @Test
    fun `buffering after playback starts shows diagnostics without another seek`() {
        isWaitingForPlayback = true
        listener.onIsPlayingChanged(false)
        advance(4_999)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
        advance(1)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `playback that recovers before the deadline does not show diagnostics`() {
        every { player.seekTo(any()) } answers { isWaitingForPlayback = true }
        service.seekTo(90_000L)
        advance(4_000)
        isWaitingForPlayback = false
        advance(1_000)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `a ready notification clears diagnostics when the player has recovered`() {
        every { player.seekTo(any()) } answers { isWaitingForPlayback = true }
        service.seekTo(90_000L)
        advance(5_000)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
        isWaitingForPlayback = false
        listener.onReady()
        assertFalse(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `an intentional pause cancels diagnostics for a stalled seek`() {
        every { player.seekTo(any()) } answers { isWaitingForPlayback = true }
        service.seekTo(90_000L)
        advance(4_000)
        service.pause()
        advance(2_000)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `stopping cancels a stalled seek deadline and clears diagnostics`() {
        every { player.seekTo(any()) } answers { isWaitingForPlayback = true }
        service.seekTo(90_000L)
        advance(4_000)
        service.stop()
        advance(2_000)
        assertFalse(service.playbackState.value.isDiagnosticVisible)
    }

    @Test
    fun `a deferred verse seek from ready preserves the buffering state`() {
        val tracker = field("highlightTracker").get(service) as HighlightTracker
        tracker.setTiming(listOf(VerseTiming(1, 0, 60_000), VerseTiming(2, 60_000, 120_000)))
        service.seekToVerse(2)
        every { player.seekTo(60_000L) } answers {
            isWaitingForPlayback = true
            listener.onBuffering()
        }

        listener.onReady()
        assertTrue(service.playbackState.value.preparing)
        advance(5_000)
        assertTrue(service.playbackState.value.isDiagnosticVisible)
    }

    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun field(name: String) = BibleAudioService::class.java.getDeclaredField(name).apply { isAccessible = true }
}
