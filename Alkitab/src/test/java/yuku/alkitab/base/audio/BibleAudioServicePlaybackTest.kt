package yuku.alkitab.base.audio

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
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

    @Test
    fun `a paused chapter request prepares the target and waits for play`() {
        verifyChapterPreparation(isAutoPlay = false)
    }

    @Test
    fun `an autoplay chapter request still starts after preparing`() {
        verifyChapterPreparation(isAutoPlay = true)
    }

    private fun verifyChapterPreparation(isAutoPlay: Boolean) {
        val service = Robolectric.buildService(BibleAudioService::class.java).get()
        val player = mockk<BibleAudioPlayer>(relaxed = true)
        val exoPlayer = mockk<ExoPlayer>(relaxed = true)
        every { player.exoPlayer } returns exoPlayer
        field("player").set(service, player)
        mockkObject(BibleAudioRepository)
        coEvery { BibleAudioRepository.buildChapterUrl(any(), any(), any(), any()) } returns "https://example.invalid/chapter.mp3"
        coEvery { BibleAudioRepository.fetchTiming(any(), any(), any(), any()) } returns null
        try {
            service.loadChapter(BibleAudioService.AudioRequest(
                versionId = "internal", audioId = "recording", bookId = 0, chapter_1 = 2,
                displayTitle = "Genesis 2", displaySubtitle = "Recording", startVerse_1 = 0,
                isAutoPlay = isAutoPlay,
            ))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, service.playbackState.value.chapter_1)
            verify { exoPlayer.playWhenReady = isAutoPlay }
            verify(exactly = 0) { exoPlayer.playWhenReady = !isAutoPlay }
            verify { exoPlayer.prepare() }
            if (!isAutoPlay) {
                verify { player.pause() }
                verify(exactly = 0) { player.play() }
                service.play()
                verify { player.play() }
            }
        } finally {
            service.stop()
            unmockkObject(BibleAudioRepository)
            AudioPlaybackCoordinator.resetForTest()
        }
    }

    @Test
    fun `play while a paused chapter is resolving is honored when preparation finishes`() {
        verifyCommandDuringResolution(isPlayRequested = true)
    }

    @Test
    fun `pause while an autoplay chapter is resolving prevents a later autoplay`() {
        verifyCommandDuringResolution(isPlayRequested = false)
    }

    private fun verifyCommandDuringResolution(isPlayRequested: Boolean) {
        val service = Robolectric.buildService(BibleAudioService::class.java).get()
        val player = mockk<BibleAudioPlayer>(relaxed = true)
        val exoPlayer = mockk<ExoPlayer>(relaxed = true)
        every { player.exoPlayer } returns exoPlayer
        field("player").set(service, player)
        val url = CompletableDeferred<String?>()
        mockkObject(BibleAudioRepository)
        coEvery { BibleAudioRepository.buildChapterUrl(any(), any(), any(), any()) } coAnswers { url.await() }
        coEvery { BibleAudioRepository.fetchTiming(any(), any(), any(), any()) } returns null
        try {
            service.loadChapter(BibleAudioService.AudioRequest(
                versionId = "internal", audioId = "recording", bookId = 0, chapter_1 = 2,
                displayTitle = "Genesis 2", displaySubtitle = "Recording", startVerse_1 = 0,
                isAutoPlay = !isPlayRequested,
            ))
            if (isPlayRequested) service.play() else service.pause()
            url.complete("https://example.invalid/chapter.mp3")
            shadowOf(Looper.getMainLooper()).idle()
            verify { exoPlayer.playWhenReady = isPlayRequested }
            verify(exactly = 0) { exoPlayer.playWhenReady = !isPlayRequested }
            verify { exoPlayer.prepare() }
        } finally {
            service.stop()
            unmockkObject(BibleAudioRepository)
            AudioPlaybackCoordinator.resetForTest()
        }
    }

    private fun field(name: String) = BibleAudioService::class.java.getDeclaredField(name).apply { isAccessible = true }
}
