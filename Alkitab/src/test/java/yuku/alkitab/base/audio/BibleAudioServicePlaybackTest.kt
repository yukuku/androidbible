package yuku.alkitab.base.audio

import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class BibleAudioServicePlaybackTest {
    @Test
    fun `external resume restarts position polling and external pause stops it`() {
        val service = Robolectric.buildService(BibleAudioService::class.java).get()
        val player = mockk<BibleAudioPlayer>(relaxed = true)
        every { player.isPlaying } returns true
        every { player.currentPositionMs } returns 1200L
        every { player.durationMs } returns 10000L
        field("player").set(service, player)
        val listener = field("playerListener").get(service) as BibleAudioPlayer.Listener
        try {
            listener.onIsPlayingChanged(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(service.playbackState.value.isPlaying)
            assertEquals(1200L, service.playbackState.value.positionMs)
            assertTrue((field("positionJob").get(service) as Job).isActive)

            every { player.isPlaying } returns false
            listener.onIsPlayingChanged(false)
            assertFalse(service.playbackState.value.isPlaying)
            assertFalse((field("positionJob").get(service) as Job).isActive)
            every { player.currentPositionMs } returns 2400L
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            assertEquals(1200L, service.playbackState.value.positionMs)

            every { player.isPlaying } returns true
            listener.onIsPlayingChanged(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2400L, service.playbackState.value.positionMs)
            assertSame(service, AudioPlaybackCoordinator.activeForTest())
        } finally {
            listener.onIsPlayingChanged(false)
            AudioPlaybackCoordinator.resetForTest()
        }
    }

    @Test
    fun `ready while paused does not start position polling`() {
        val service = Robolectric.buildService(BibleAudioService::class.java).get()
        val player = mockk<BibleAudioPlayer>(relaxed = true)
        field("player").set(service, player)
        val listener = field("playerListener").get(service) as BibleAudioPlayer.Listener
        listener.onReady()
        assertNull(field("positionJob").get(service))
        assertFalse(service.playbackState.value.isPlaying)
        verify(exactly = 0) { player.currentPositionMs }
    }

    private fun field(name: String) = BibleAudioService::class.java.getDeclaredField(name).apply { isAccessible = true }
}
