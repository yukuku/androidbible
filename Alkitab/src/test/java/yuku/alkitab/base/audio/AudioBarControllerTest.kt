package yuku.alkitab.base.audio

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.audio.builtin.BuiltInAudioCatalog
import yuku.alkitab.base.audio.ui.AudioSourceOption

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AudioBarControllerTest {

    private val catalog = BuiltInAudioCatalog(ApplicationProvider.getApplicationContext())

    @Test
    fun `download request targets the selected built-in chapter`() {
        val source = AudioSourceOption(
            versionId = "preset/en-web",
            shortName = "WEB",
            audioId = BuiltInAudioCatalog.AUDIO_ID,
            title = "WEB — David Williams (public domain)",
        )

        assertEquals(
            AudioDownloadRequest(
                audioId = BuiltInAudioCatalog.AUDIO_ID,
                bookId = 0,
                chapter1 = 1,
                url = "https://audiotreasure.com/content/WEBD_AT/01_Genesis_01.mp3",
            ),
            AudioBarController.downloadRequest(source, bookId = 0, chapter1 = 1, catalog = catalog),
        )
    }

    @Test
    fun `download request is absent for remote and invalid chapters`() {
        val remote = AudioSourceOption("preset/in-tb", "TB", "alkitabsuara", "Alkitab Suara")
        val web = AudioSourceOption(
            "preset/en-web",
            "WEB",
            BuiltInAudioCatalog.AUDIO_ID,
            "WEB — David Williams (public domain)",
        )

        assertNull(AudioBarController.downloadRequest(remote, 0, 1, catalog))
        assertNull(AudioBarController.downloadRequest(web, 0, 51, catalog))
    }

    @Test
    fun `recorded availability distinguishes coverage from playback failure`() {
        assertEquals(
            RecordedAudioAvailability.Available("human"),
            AudioBarController.recordedAudioAvailability(audioId = "human", playbackFailed = false),
        )
        assertEquals(
            RecordedAudioAvailability.Failed("human"),
            AudioBarController.recordedAudioAvailability(audioId = "human", playbackFailed = true),
        )
        assertEquals(
            RecordedAudioAvailability.Unavailable,
            AudioBarController.recordedAudioAvailability(audioId = null, playbackFailed = true),
        )
    }
}
