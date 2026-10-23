package yuku.alkitab.base.audio

import android.content.Context
import android.content.ServiceConnection
import android.os.Looper
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.Shadows.shadowOf
import yuku.alkitab.base.audio.ui.AudioBarCommand
import yuku.alkitab.base.audio.ui.AudioSourceOption
import yuku.alkitab.model.Book

internal class AudioBarControllerFixture {
    var chapter = 1
    var isBindSuccessful = true
    val sourceA = AudioSourceOption("internal", "V", "a", "A")
    var sources = listOf(sourceA)
    val book = Book().apply {
        bookId = 0
        shortName = "Genesis"
        chapter_count = 50
        verse_counts = IntArray(50) { 50 }
    }
    val context = mockk<Context>(relaxed = true)
    val host = mockk<AudioBarController.Host>(relaxed = true)
    val service = mockk<BibleAudioService>(relaxed = true)
    val state = MutableStateFlow(PlaybackState.IDLE)
    val requests = mutableListOf<BibleAudioService.AudioRequest>()
    val displayedChapters = mutableListOf<Int>()
    lateinit var connection: ServiceConnection
    val controller = AudioBarController(context)

    init {
        every { context.bindService(any(), any<ServiceConnection>(), any<Int>()) } answers {
            connection = secondArg()
            isBindSuccessful
        }
        every { host.audioCurrentBook() } returns book
        every { host.audioCurrentChapter1() } answers { chapter }
        every { host.audioVisibleVersionIds() } returns listOf("internal")
        every { host.audioAvailableSources() } answers { sources }
        every { host.audioBookInVersion("internal", 0) } returns book
        every { host.audioVersionShortName("internal") } returns "V"
        every { host.audioDisplayChapter(any(), any()) } answers {
            chapter = secondArg()
            displayedChapters += chapter
            controller.onChapterChanged()
        }
        every { service.playbackState } returns state
        every { service.pause() } answers { state.value = state.value.copy(isPlaying = false) }
        every { service.peekVerseAt(any()) } returns 5
        every { service.loadChapter(any()) } answers {
            val request = firstArg<BibleAudioService.AudioRequest>()
            requests += request
            state.value = PlaybackState.IDLE.copy(
                versionId = request.versionId, audioId = request.audioId,
                bookId = request.bookId, chapter_1 = request.chapter_1,
                isPlaying = request.isAutoPlay, preparing = true,
            )
        }
        controller.attach(host, ComposeView(ApplicationProvider.getApplicationContext()))
    }

    fun activeState(): PlaybackState = PlaybackState.IDLE.copy(
        versionId = "internal", audioId = "a", bookId = 0, chapter_1 = 1,
        verse_1 = 3, isPlaying = true,
    )

    fun connect(initialState: PlaybackState) {
        state.value = initialState
        connection.onServiceConnected(null, BibleAudioService.LocalBinder(service))
        shadowOf(Looper.getMainLooper()).idle()
    }

    fun command(command: AudioBarCommand) {
        controller.onCommand(command)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
