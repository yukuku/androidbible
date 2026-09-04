package yuku.alkitab.base.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import yuku.alkitab.base.audio.RecordedAudioAvailability

class ListeningSourceResolverTest {

    private val resolver = ListeningSourceResolver()

    @Test
    fun `covering human recording always wins`() {
        assertEquals(
            ListeningSource.Recorded("human"),
            resolver.resolve(RecordedAudioAvailability.Available("human"), explicitTtsAfterFailure = false),
        )
        assertEquals(
            ListeningSource.Recorded("human"),
            resolver.resolve(RecordedAudioAvailability.Available("human"), explicitTtsAfterFailure = true),
        )
    }

    @Test
    fun `no recorded narration selects Google TTS fallback`() {
        assertEquals(
            ListeningSource.GoogleTts,
            resolver.resolve(RecordedAudioAvailability.Unavailable, explicitTtsAfterFailure = false),
        )
    }

    @Test
    fun `recording failure requires an explicit TTS fallback action`() {
        assertEquals(
            ListeningSource.Unavailable(ListeningUnavailableReason.RECORDED_FAILED),
            resolver.resolve(RecordedAudioAvailability.Failed("human"), explicitTtsAfterFailure = false),
        )
        assertEquals(
            ListeningSource.GoogleTts,
            resolver.resolve(RecordedAudioAvailability.Failed("human"), explicitTtsAfterFailure = true),
        )
    }
}
