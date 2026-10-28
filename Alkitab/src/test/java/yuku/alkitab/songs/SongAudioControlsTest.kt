package yuku.alkitab.songs

import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class SongAudioControlsTest {
    @Test
    fun `seeking clamps both ends to the loaded song duration`() {
        val service = Robolectric.buildService(SongAudioService::class.java).get()
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.duration } returns 10000L
        SongAudioService::class.java.getDeclaredField("player").apply { isAccessible = true }.set(service, player)
        service.seekTo(-50)
        service.seekTo(1234)
        service.seekTo(20000)
        verify { player.seekTo(0L) }
        verify { player.seekTo(1234L) }
        verify { player.seekTo(10000L) }
    }

    @Test
    fun `playing a repeating song can still be paused`() {
        val controller = SongAudioController(mockk(relaxed = true))
        val service = mockk<SongAudioService>(relaxed = true)
        SongAudioController::class.java.getDeclaredField("service").apply { isAccessible = true }.set(controller, service)
        controller.state = MediaController.State.playing
        controller.playOrPause(true)
        verify(exactly = 1) { service.pause() }
        assertTrue(controller.isLooping)
    }

    @Test
    fun `changing repeat updates the service and reset clears it`() {
        val controller = SongAudioController(mockk(relaxed = true))
        val service = mockk<SongAudioService>(relaxed = true)
        SongAudioController::class.java.getDeclaredField("service").apply { isAccessible = true }.set(controller, service)
        controller.setLoop(true)
        verify { service.setLoop(true) }
        assertTrue(controller.isLooping)
        controller.reset()
        assertFalse(controller.isLooping)
    }
}
