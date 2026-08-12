package yuku.alkitab.base.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-logic tests for [stripErrorCodePrefix]. */
class AudioErrorCodeTest {

    @Test
    fun `the media3 ERROR_CODE_ prefix is stripped for display`() {
        assertEquals(
            "IO_NETWORK_CONNECTION_FAILED",
            stripErrorCodePrefix("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"),
        )
    }

    @Test
    fun `a name without the prefix is left alone`() {
        assertEquals("no_audio_for_version", stripErrorCodePrefix("no_audio_for_version"))
    }

    @Test
    fun `only a leading prefix is stripped, not one appearing mid-string`() {
        assertEquals("IO_ERROR_CODE_X", stripErrorCodePrefix("ERROR_CODE_IO_ERROR_CODE_X"))
    }
}
