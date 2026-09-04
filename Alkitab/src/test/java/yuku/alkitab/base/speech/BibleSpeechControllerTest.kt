package yuku.alkitab.base.speech

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import yuku.alkitab.base.audio.AudioPlaybackCoordinator

class BibleSpeechControllerTest {
    private lateinit var engine: FakeSpeechEngine
    private lateinit var controller: BibleSpeechController

    @Before
    fun setUp() {
        AudioPlaybackCoordinator.resetForTest()
        engine = FakeSpeechEngine()
        controller = BibleSpeechController(engine)
    }

    @After
    fun tearDown() {
        controller.close()
        AudioPlaybackCoordinator.resetForTest()
    }

    @Test
    fun `speak cleans text selects locale and starts first passage`() {
        controller.speak(listOf(passage("one", "id-ID", "@@@0Kasih@8itu sabar")))

        assertEquals("id-ID", engine.languageTag)
        assertEquals(listOf(Spoken("one", "Kasih\nitu sabar")), engine.spoken)
        assertEquals(SpeechState.Speaking("one", 0, 1), controller.state.value)
    }

    @Test
    fun `starting TTS stops the previous audio owner`() {
        val recordedSession = FakeSession()
        AudioPlaybackCoordinator.acquire(recordedSession)

        controller.speak(listOf(passage("one")))

        assertEquals(1, recordedSession.stopCount)
        assertEquals(controller, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `unsupported locale reports error without speaking or owning audio`() {
        engine.languageAvailable = false

        controller.speak(listOf(passage("one", "id-ID")))

        assertEquals(SpeechState.Error(SpeechError.LANGUAGE_UNAVAILABLE), controller.state.value)
        assertTrue(engine.spoken.isEmpty())
        assertEquals(null, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `completion advances one passage at a time and final completion releases audio`() {
        controller.speak(listOf(passage("one"), passage("two")))

        engine.complete("one")
        assertEquals(listOf("one", "two"), engine.spoken.map { it.id })
        assertEquals(SpeechState.Speaking("two", 1, 2), controller.state.value)

        engine.complete("two")
        assertEquals(SpeechState.Idle, controller.state.value)
        assertEquals(null, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `stale completion is ignored`() {
        controller.speak(listOf(passage("one"), passage("two")))

        engine.complete("not-current")

        assertEquals(listOf("one"), engine.spoken.map { it.id })
        assertEquals(SpeechState.Speaking("one", 0, 2), controller.state.value)
    }

    @Test
    fun `next and previous stop the current utterance and keep deterministic position`() {
        controller.speak(listOf(passage("one"), passage("two"), passage("three")))

        controller.next()
        assertEquals("two", engine.spoken.last().id)
        assertEquals(SpeechState.Speaking("two", 1, 3), controller.state.value)

        controller.previous()
        assertEquals("one", engine.spoken.last().id)
        assertEquals(SpeechState.Speaking("one", 0, 3), controller.state.value)
        assertEquals(2, engine.stopCount)
    }

    @Test
    fun `engine preparation failure reports unavailable`() {
        engine.prepareResult = false

        controller.speak(listOf(passage("one")))

        assertEquals(SpeechState.Error(SpeechError.ENGINE_UNAVAILABLE), controller.state.value)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `engine error stops the queue and releases audio`() {
        controller.speak(listOf(passage("one"), passage("two")))

        engine.fail("one")

        assertEquals(SpeechState.Error(SpeechError.SPEAK_FAILED), controller.state.value)
        assertEquals(null, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `close stops and shuts down engine`() {
        controller.speak(listOf(passage("one")))

        controller.close()

        assertEquals(SpeechState.Idle, controller.state.value)
        assertEquals(1, engine.shutdownCount)
        assertEquals(null, AudioPlaybackCoordinator.activeForTest())
    }

    private fun passage(id: String, languageTag: String = "en-US", text: String = id) =
        SpeechPassage(id, "Reference $id", languageTag, text)

    private data class Spoken(val id: String, val text: String)

    private class FakeSpeechEngine : SpeechEngine {
        private lateinit var listener: SpeechEngine.Listener
        var prepareResult = true
        var languageAvailable = true
        var languageTag: String? = null
        val spoken = mutableListOf<Spoken>()
        var stopCount = 0
        var shutdownCount = 0

        override fun setListener(listener: SpeechEngine.Listener) {
            this.listener = listener
        }

        override fun prepare(onResult: (Boolean) -> Unit) = onResult(prepareResult)

        override fun isLanguageAvailable(languageTag: String): Boolean = languageAvailable

        override fun setLanguage(languageTag: String): Boolean {
            this.languageTag = languageTag
            return languageAvailable
        }

        override fun speak(id: String, text: String): Boolean {
            spoken += Spoken(id, text)
            return true
        }

        override fun stop() {
            stopCount++
        }

        override fun shutdown() {
            shutdownCount++
        }

        fun complete(id: String) = listener.onDone(id)
        fun fail(id: String) = listener.onError(id)
    }

    private class FakeSession : AudioPlaybackCoordinator.Session {
        var stopCount = 0
        override fun stopPlayback() {
            stopCount++
            AudioPlaybackCoordinator.release(this)
        }
    }
}
