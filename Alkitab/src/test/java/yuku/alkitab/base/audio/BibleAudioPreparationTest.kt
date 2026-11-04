package yuku.alkitab.base.audio

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class BibleAudioPreparationTest {
    @Test
    fun `targeted navigation prepares a chapter without autoplay`() = verifyPreparation(false, null)

    @Test
    fun `ordinary chapter loading retains autoplay`() = verifyPreparation(true, null)

    @Test
    fun `pausing while the URL resolves prevents autoplay`() = verifyPreparation(true, false)

    @Test
    fun `playing while the URL resolves resumes the prepared chapter`() = verifyPreparation(false, true)

    @Test
    fun `media session pause while the URL resolves remains authoritative`() = verifyPreparation(true, false, true)

    @Test
    fun `media session play while the URL resolves remains authoritative`() = verifyPreparation(false, true, true)

    private fun verifyPreparation(isAutoPlay: Boolean, isCommandPlay: Boolean?, isMediaSessionCommand: Boolean = false) {
        val playback = testBiblePlayback()
        val player = mockk<BibleAudioPlayer>(relaxed = true)
        val exoPlayer = mockk<ExoPlayer>(relaxed = true)
        var isPlayWhenReady = !isAutoPlay
        every { player.exoPlayer } returns exoPlayer
        every { exoPlayer.playWhenReady } answers { isPlayWhenReady }
        every { exoPlayer.playWhenReady = any() } answers { isPlayWhenReady = firstArg() }
        every { player.play() } answers { exoPlayer.playWhenReady = true }
        every { player.pause() } answers { exoPlayer.playWhenReady = false }
        BibleAudioPlayback::class.java.getDeclaredField("playerInstance")
            .apply { isAccessible = true }.set(playback, player)
        val url = CompletableDeferred<String?>()
        mockkObject(BibleAudioRepository)
        coEvery { BibleAudioRepository.buildChapterUrl(any(), any(), any(), any()) } coAnswers { url.await() }
        coEvery { BibleAudioRepository.fetchTiming(any(), any(), any(), any()) } returns null
        try {
            playback.loadChapter(BibleAudioPlayback.AudioRequest(
                "internal", "recording", 0, 2, "Genesis 2", "Recording", 0, isAutoPlay,
            ))
            verifyOrder {
                exoPlayer.stop()
                exoPlayer.playWhenReady = isAutoPlay
            }
            assertEquals(isAutoPlay, isPlayWhenReady)
            assertEquals(2, playback.playbackState.value.chapter_1)
            verify(exactly = 0) { exoPlayer.prepare() }
            if (isCommandPlay != null) {
                if (isMediaSessionCommand) exoPlayer.playWhenReady = isCommandPlay
                else if (isCommandPlay) playback.play() else playback.pause()
            }
            url.complete("https://example.invalid/chapter.mp3")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(isCommandPlay ?: isAutoPlay, isPlayWhenReady)
            verify(exactly = 1) { exoPlayer.setMediaItem(any()) }
            verify(exactly = 1) { exoPlayer.prepare() }
        } finally {
            playback.stop()
            playback.onDestroy()
            unmockkObject(BibleAudioRepository)
        }
    }
}
