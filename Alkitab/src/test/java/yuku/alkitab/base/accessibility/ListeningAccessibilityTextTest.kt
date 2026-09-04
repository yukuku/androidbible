package yuku.alkitab.base.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test
import yuku.alkitab.base.audio.RecordedAudioAvailability

class ListeningAccessibilityTextTest {
    private val text = ListeningAccessibilityText(
        resultTemplate = "Hasil %1\$d dari %2\$d. %3\$s. %4\$s. Ketuk dua kali untuk membuka.",
        recordedAction = "Dengarkan dengan rekaman naratif",
        ttsAction = "Bacakan dengan Google TTS",
        failedAction = "Rekaman gagal. Pilih Google TTS untuk melanjutkan",
    )

    @Test
    fun `result description includes rank reference text and action`() {
        assertEquals(
            "Hasil 2 dari 30. Yohanes 3:16. Karena begitu besar kasih Allah. Ketuk dua kali untuk membuka.",
            text.resultDescription(2, 30, "Yohanes 3:16", "Karena begitu besar kasih Allah."),
        )
    }

    @Test
    fun `listen state names recorded source before TTS`() {
        assertEquals(
            "Dengarkan dengan rekaman naratif",
            text.listenAction(RecordedAudioAvailability.Available("human")),
        )
        assertEquals(
            "Bacakan dengan Google TTS",
            text.listenAction(RecordedAudioAvailability.Unavailable),
        )
    }

    @Test
    fun `failed recording announces explicit choice`() {
        assertEquals(
            "Rekaman gagal. Pilih Google TTS untuk melanjutkan",
            text.listenAction(RecordedAudioAvailability.Failed("human")),
        )
    }
}
