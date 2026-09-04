package yuku.alkitab.base.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yuku.alkitab.base.audio.AudioPlaybackCoordinator
import yuku.alkitab.base.util.FormattedVerseText

/** Owns a deterministic, one-utterance-at-a-time Bible reading queue. */
class BibleSpeechController(
    private val engine: SpeechEngine,
) : AudioPlaybackCoordinator.Session, SpeechEngine.Listener, AutoCloseable {
    private val mutableState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = mutableState.asStateFlow()

    private var queue: List<SpeechPassage> = emptyList()
    private var index = 0
    private var requestGeneration = 0L
    private var closed = false

    init {
        engine.setListener(this)
    }

    fun speak(passages: List<SpeechPassage>) {
        if (closed || passages.isEmpty()) return

        stopQueue(updateState = false)
        queue = passages
        index = 0
        val generation = ++requestGeneration
        mutableState.value = SpeechState.Preparing(queue.size)
        engine.prepare { ready ->
            if (closed || generation != requestGeneration) return@prepare
            if (!ready) {
                fail(SpeechError.ENGINE_UNAVAILABLE)
            } else {
                speakCurrent()
            }
        }
    }

    fun stop() {
        if (closed) return
        requestGeneration++
        stopQueue(updateState = true)
    }

    fun next() {
        if (closed || index >= queue.lastIndex) return
        engine.stop()
        index++
        speakCurrent()
    }

    fun previous() {
        if (closed || index <= 0 || queue.isEmpty()) return
        engine.stop()
        index--
        speakCurrent()
    }

    override fun onDone(id: String) {
        val current = queue.getOrNull(index) ?: return
        if (current.id != id) return

        if (index < queue.lastIndex) {
            index++
            speakCurrent()
        } else {
            queue = emptyList()
            index = 0
            mutableState.value = SpeechState.Idle
            AudioPlaybackCoordinator.release(this)
        }
    }

    override fun onError(id: String) {
        val current = queue.getOrNull(index) ?: return
        if (current.id == id) fail(SpeechError.SPEAK_FAILED)
    }

    override fun stopPlayback() = stop()

    override fun close() {
        if (closed) return
        requestGeneration++
        stopQueue(updateState = true)
        engine.shutdown()
        closed = true
    }

    private fun speakCurrent() {
        val passage = queue.getOrNull(index) ?: run {
            fail(SpeechError.SPEAK_FAILED)
            return
        }
        if (!engine.isLanguageAvailable(passage.languageTag) || !engine.setLanguage(passage.languageTag)) {
            fail(SpeechError.LANGUAGE_UNAVAILABLE)
            return
        }

        val cleanText = FormattedVerseText.removeSpecialCodes(
            passage.text.replace("@8", "\n"),
            true,
        ).orEmpty().trim()
        if (cleanText.isEmpty()) {
            fail(SpeechError.SPEAK_FAILED)
            return
        }

        AudioPlaybackCoordinator.acquire(this)
        if (!engine.speak(passage.id, cleanText)) {
            fail(SpeechError.SPEAK_FAILED)
            return
        }
        mutableState.value = SpeechState.Speaking(passage.id, index, queue.size)
    }

    private fun fail(error: SpeechError) {
        queue = emptyList()
        index = 0
        mutableState.value = SpeechState.Error(error)
        AudioPlaybackCoordinator.release(this)
    }

    private fun stopQueue(updateState: Boolean) {
        if (queue.isNotEmpty() || state.value is SpeechState.Speaking) engine.stop()
        queue = emptyList()
        index = 0
        AudioPlaybackCoordinator.release(this)
        if (updateState) mutableState.value = SpeechState.Idle
    }
}
