package yuku.alkitab.base.audio

import android.os.Looper
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import yuku.alkitab.base.audio.ui.AudioBarCommand

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class AudioBarControllerStateTest {
    private val activeSession = BibleAudioService::class.java.getDeclaredField("hasActiveSession").apply { isAccessible = true }
    private var isOriginalSessionActive = false
    private val fixtures = mutableListOf<AudioBarControllerFixture>()

    @Before
    fun setUp() {
        isOriginalSessionActive = activeSession.getBoolean(null)
        activeSession.setBoolean(null, false)
        AudioSetsRepository.resetForTest()
        AudioSetsRepository.presetNameResolver = AudioSetsRepository.PresetNameResolver { "audit" }
        AudioSetsRepository.http = AudioHttp { """
            {"schema":1,"preset":"audit","sets":[
                {"audioId":"a","title":"A","hasTiming":true,"books_1":[1],"mp3UrlTemplate":"/audio/a/{chapter_1}.mp3","timingUrlTemplate":"/timing/a/{chapter_1}"},
                {"audioId":"b","title":"B","hasTiming":true,"books_1":[1],"mp3UrlTemplate":"/audio/b/{chapter_1}.mp3","timingUrlTemplate":"/timing/b/{chapter_1}"}
            ]}
        """.trimIndent() }
        runBlocking { AudioSetsRepository.setsFor("internal") }
    }

    @After
    fun tearDown() {
        fixtures.forEach { it.controller.detach() }
        activeSession.setBoolean(null, isOriginalSessionActive)
        AudioSetsRepository.resetForTest()
    }

    @Test
    fun `a normal queued start preserves the requested verse`() {
        val f = fixture()
        f.controller.showFromVerse(9)
        f.connect(PlaybackState.IDLE)
        assertEquals(9, f.requests.single().startVerse_1)
        assertTrue(f.requests.single().isAutoPlay)
        assertTrue(f.controller.uiState.value.visible)
    }

    @Test
    fun `navigation with connected audio stays paused at the target`() {
        val f = fixture()
        f.controller.show()
        f.connect(PlaybackState.IDLE)
        f.controller.navigateToPassage {
            f.chapter = 2
            f.controller.onChapterChanged()
        }
        assertEquals(2, f.requests.last().chapter_1)
        assertFalse(f.requests.last().isAutoPlay)
        assertEquals(2, f.chapter)
    }

    @Test
    fun `a later play from verse supersedes navigation queued before connection`() {
        val f = fixture()
        f.controller.show()
        f.controller.navigateToPassage {
            f.chapter = 2
            f.controller.onChapterChanged()
        }
        f.controller.showFromVerse(9)
        f.connect(PlaybackState.IDLE)
        assertEquals("the latest play action must retain verse 9", 9, f.requests.single().startVerse_1)
        assertTrue("the latest play action must request playback", f.requests.single().isAutoPlay)
    }

    @Test
    fun `navigation clears the drag preview from the previous chapter`() {
        val f = fixture()
        f.controller.show()
        f.connect(PlaybackState.IDLE)
        f.command(AudioBarCommand.SeekDrag(1000L))
        assertEquals(5, f.controller.uiState.value.verse_1)
        f.controller.navigateToPassage {
            f.chapter = 2
            f.controller.onChapterChanged()
        }
        assertEquals("a paused new chapter must not retain the old drag highlight", 0, f.controller.uiState.value.verse_1)
    }

    @Test
    fun `restoration recovers the selected source when availability arrives later`() {
        val f = fixture()
        f.sources = emptyList()
        activeSession.setBoolean(null, true)
        f.controller.reshowIfSessionActive()
        f.connect(f.activeState())
        f.sources = listOf(f.sourceA)
        f.controller.refreshSetChoices()
        f.chapter = 2
        f.controller.onChapterChanged()
        assertEquals("restored audio must follow the reader after availability resolves", 2, f.requests.singleOrNull()?.chapter_1)
    }

    @Test
    fun `a failed bind does not leave an endless preparing state`() {
        val f = fixture()
        f.isBindSuccessful = false
        f.controller.show()
        shadowOf(Looper.getMainLooper()).idle()
        val ui = f.controller.uiState.value
        assertTrue("failed binding must either close or expose an error instead of an endless spinner", !ui.visible || !ui.preparing || ui.error != null)
    }

    @Test
    fun `an old initial snapshot cannot navigate while a newer load is queued`() {
        val f = fixture()
        f.chapter = 2
        f.controller.showFromVerse(9)
        f.connect(f.activeState())
        assertTrue("activity-driven pending load must not navigate to old audio and back: " + f.displayedChapters, f.displayedChapters.isEmpty())
    }

    @Test
    fun `restoration cannot overwrite a recording chosen while binding`() {
        val f = fixture()
        activeSession.setBoolean(null, true)
        f.controller.reshowIfSessionActive()
        f.controller.showFromVerse(9)
        f.command(AudioBarCommand.PickSet("internal", "b"))
        f.connect(f.activeState())
        assertEquals("the user's latest recording selection must win", "b", f.requests.single().audioId)
    }

    @Test
    fun `play from verse resumes a paused timed session`() {
        val f = fixture()
        f.controller.show()
        f.connect(PlaybackState.IDLE)
        f.service.pause()
        shadowOf(Looper.getMainLooper()).idle()
        f.controller.showFromVerse(9)
        verify { f.service.seekToVerse(9) }
        verify { f.service.play() }
    }

    @Test
    fun `a recording changed by another reader is reflected by the chooser`() {
        val f = fixture()
        f.controller.show()
        f.connect(PlaybackState.IDLE)
        f.state.value = f.activeState().copy(audioId = "b")
        shadowOf(Looper.getMainLooper()).idle()
        f.command(AudioBarCommand.OpenSetSheet)
        val selected = f.controller.uiState.value.setGroups.orEmpty()
            .flatMap { it.options }.single { it.selected }
        assertEquals("the chosen row must match the recording the service is playing", "b", selected.audioId)
    }

    @Test
    fun `a late drag commit cannot seek in the newly selected chapter`() {
        val f = fixture()
        f.controller.show()
        f.connect(PlaybackState.IDLE)
        f.command(AudioBarCommand.SeekDrag(1000L))
        assertEquals(1000L, f.controller.uiState.value.positionMs)
        f.controller.navigateToPassage {
            f.chapter = 2
            f.controller.onChapterChanged()
        }
        f.command(AudioBarCommand.SeekCommit(1000L))
        verify(exactly = 0) { f.service.seekTo(any()) }
        assertEquals(0L, f.controller.uiState.value.positionMs)
    }

    @Test
    fun `a drag commit uses the latest preview even before the view recomposes`() {
        val f = fixture()
        f.controller.show()
        f.connect(PlaybackState.IDLE)
        f.command(AudioBarCommand.SeekDrag(1000L))
        f.command(AudioBarCommand.SeekDrag(2000L))
        f.command(AudioBarCommand.SeekCommit(1000L))
        verify { f.service.seekTo(2000L) }
        verify(exactly = 0) { f.service.seekTo(1000L) }
    }

    @Test
    fun `repicking the pending recording preserves the requested verse before connection`() {
        val f = fixture()
        f.controller.showFromVerse(9)
        f.command(AudioBarCommand.PickSet("internal", "a"))
        f.connect(PlaybackState.IDLE)
        assertEquals(9, f.requests.single().startVerse_1)
        assertTrue(f.requests.single().isAutoPlay)
    }

    @Test
    fun `a newer paused navigation replaces a queued play request`() {
        val f = fixture()
        f.controller.showFromVerse(9)
        f.controller.navigateToPassage {
            f.chapter = 2
            f.controller.onChapterChanged()
        }
        f.connect(PlaybackState.IDLE)
        assertEquals(2, f.requests.single().chapter_1)
        assertEquals(0, f.requests.single().startVerse_1)
        assertFalse(f.requests.single().isAutoPlay)
    }

    @Test
    fun `switching dialogs leaves only the latest dialog open`() {
        val f = fixture()
        f.controller.show()
        f.connect(PlaybackState.IDLE)
        f.command(AudioBarCommand.Speed)
        assertTrue(f.controller.uiState.value.showSpeedSheet)
        f.command(AudioBarCommand.OpenLogSheet)
        assertTrue(f.controller.uiState.value.showLogSheet)
        assertFalse(f.controller.uiState.value.showSpeedSheet)
        f.command(AudioBarCommand.OpenSetSheet)
        assertNotNull(f.controller.uiState.value.setGroups)
        assertFalse(f.controller.uiState.value.showLogSheet)
        assertFalse(f.controller.uiState.value.showSpeedSheet)
    }

    @Test
    fun `a failed binding can be retried without retaining its old pending request`() {
        val f = fixture()
        f.isBindSuccessful = false
        f.controller.showFromVerse(9)
        assertFalse(f.controller.isBarVisible)
        f.isBindSuccessful = true
        f.chapter = 2
        f.controller.showFromVerse(7)
        f.connect(PlaybackState.IDLE)
        assertEquals(2, f.requests.single().chapter_1)
        assertEquals(7, f.requests.single().startVerse_1)
    }

    @Test
    fun `retry retains the recording metadata before invalidating availability`() {
        val f = fixture()
        f.controller.show()
        f.connect(PlaybackState.IDLE)
        f.command(AudioBarCommand.Retry)
        assertEquals("V · A", f.requests.last().displaySubtitle)
    }

    @Test
    fun `a different reader version releases this bar without stopping its playback`() {
        val f = fixture()
        f.controller.show()
        f.connect(PlaybackState.IDLE)
        f.state.value = f.activeState().copy(versionId = "preset/other")
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(f.controller.isBarVisible)
        verify(exactly = 0) { f.service.stop() }
        verify(exactly = 1) { f.context.unbindService(f.connection) }
    }

    private fun fixture(): AudioBarControllerFixture = AudioBarControllerFixture().also { fixtures += it }

}
