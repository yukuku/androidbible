package yuku.alkitab.base.speech

sealed interface SpeechState {
    data object Idle : SpeechState
    data class Preparing(val total: Int) : SpeechState
    data class Speaking(val id: String, val index: Int, val total: Int) : SpeechState
    data class Error(val error: SpeechError) : SpeechState
}

enum class SpeechError {
    ENGINE_UNAVAILABLE,
    LANGUAGE_UNAVAILABLE,
    SPEAK_FAILED,
}
