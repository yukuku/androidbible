package yuku.alkitab.base.speech

/** Narrow boundary around Android text-to-speech, kept fakeable for unit tests. */
interface SpeechEngine {
    interface Listener {
        fun onDone(id: String)
        fun onError(id: String)
    }

    fun setListener(listener: Listener)

    /** Initializes the engine if necessary and reports readiness exactly once. */
    fun prepare(onResult: (Boolean) -> Unit)

    fun isLanguageAvailable(languageTag: String): Boolean
    fun setLanguage(languageTag: String): Boolean
    fun speak(id: String, text: String): Boolean
    fun stop()
    fun shutdown()
}
