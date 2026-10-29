package yuku.alkitab.songs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongMediaToolbarStateTest {
    private val audio = listOf(SongAudioClip("https://example.com/song.mp3"))
    private val videos = listOf(SongYoutubeVideo("abcdefghijk", "Song", "Channel", null, null, false, null))

    @Test
    fun `a complete empty response shows unavailable while incomplete results allow retry`() {
        assertEquals(SongMediaToolbarState.Unavailable, state(SongMedia(emptyList(), emptyList(), false), false, false))
        assertEquals(SongMediaToolbarState.Retry, state(SongMedia(emptyList(), emptyList(), true), false, false))
        assertEquals(SongMediaToolbarState.Retry, state(null, false, true))
    }

    @Test
    fun `the icon distinguishes audio videos and both types`() {
        assertEquals(SongMediaToolbarState.Audio, state(SongMedia(audio, emptyList(), false), false, false))
        assertEquals(SongMediaToolbarState.Videos, state(SongMedia(emptyList(), videos, false), false, false))
        assertEquals(SongMediaToolbarState.AudioAndVideos, state(SongMedia(audio, videos, false), false, false))
    }

    @Test
    fun `partial responses still advertise the media that is available`() {
        assertEquals(SongMediaToolbarState.Audio, state(SongMedia(audio, emptyList(), true), false, false))
        assertEquals(SongMediaToolbarState.Videos, state(SongMedia(emptyList(), videos, true), false, false))
    }

    @Test
    fun `loading and failures override retained results during retry`() {
        val retained = SongMedia(audio, videos, false)
        assertEquals(SongMediaToolbarState.Loading, state(retained, true, false))
        assertEquals(SongMediaToolbarState.Retry, state(retained, false, true))
    }

    @Test
    fun `switching songs never advertises the previous songs media`() {
        val old = SongMediaViewModel.UiState("KJ", "1", SongMedia(audio, videos, false), false, false)
        assertEquals(SongMediaToolbarState.Loading, old.toolbarState("KJ", "2"))
        assertEquals(SongMediaToolbarState.Loading, old.toolbarState("PKJ", "1"))
    }

    @Test
    fun `a missing current song has no toolbar action`() {
        val old = SongMediaViewModel.UiState("KJ", "1", SongMedia(audio, videos, false), false, false)
        assertEquals(SongMediaToolbarState.Hidden, old.toolbarState(null, null))
    }

    @Test
    fun `a current song with no matching result shows loading instead of empty`() {
        val initial = SongMediaViewModel.UiState(null, null, null, false, false)
        assertEquals(SongMediaToolbarState.Loading, initial.toolbarState("KJ", "1"))
    }

    @Test
    fun `every visible state including loading unavailable and retry is actionable`() {
        for (state in SongMediaToolbarState.entries.filter { it != SongMediaToolbarState.Hidden }) {
            assertTrue(state.name, state.isVisible)
            assertTrue(state.name, state.isEnabled)
        }
        assertFalse(SongMediaToolbarState.Hidden.isVisible)
        assertFalse(SongMediaToolbarState.Hidden.isEnabled)
    }

    private fun state(media: SongMedia?, isLoading: Boolean, isError: Boolean): SongMediaToolbarState =
        SongMediaViewModel.UiState("KJ", "1", media, isLoading, isError).toolbarState("KJ", "1")
}
