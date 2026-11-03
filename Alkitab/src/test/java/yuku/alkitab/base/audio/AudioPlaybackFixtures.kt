package yuku.alkitab.base.audio

import io.mockk.every
import io.mockk.mockk
import org.robolectric.RuntimeEnvironment
import yuku.alkitab.songs.SongAudioPlayback

fun testAudioOwner(): AudioService = mockk(relaxed = true) {
    every { isActive(any()) } returns true
}

fun testBiblePlayback(): BibleAudioPlayback = BibleAudioPlayback(RuntimeEnvironment.getApplication(), testAudioOwner())
fun testSongPlayback(): SongAudioPlayback = SongAudioPlayback(RuntimeEnvironment.getApplication(), testAudioOwner())
