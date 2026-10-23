package yuku.alkitab.base.audio

import yuku.alkitab.base.audio.ui.AudioSourceOption

internal data class AudioBarTarget(val bookId: Int, val chapter1: Int) {
    fun matches(playback: PlaybackState): Boolean = bookId == playback.bookId && chapter1 == playback.chapter_1
}

internal sealed interface AudioBarPendingAction {
    data object Restore : AudioBarPendingAction
    data class FollowReader(val target: AudioBarTarget, val isAutoPlay: Boolean) : AudioBarPendingAction
    data class Load(val request: BibleAudioService.AudioRequest) : AudioBarPendingAction
}

internal sealed interface AudioBarDialog {
    data object None : AudioBarDialog
    data class Source(val target: AudioBarTarget, val startVerse1: Int) : AudioBarDialog
    data class Recording(val target: AudioBarTarget, val startVerse1: Int, val isTimedOnly: Boolean) : AudioBarDialog
    data object Speed : AudioBarDialog
    data object Log : AudioBarDialog
}

internal data class AudioBarDragPreview(val positionMs: Long, val verse1: Int)

internal data class AudioBarSession(
    val isRequestedVisible: Boolean,
    val source: AudioSourceOption?,
    val pending: AudioBarPendingAction?,
    val playback: PlaybackState,
    val dialog: AudioBarDialog,
    val preview: AudioBarDragPreview?,
    val isTimingAvailable: Boolean,
) {
    companion object {
        val HIDDEN = AudioBarSession(
            isRequestedVisible = false,
            source = null,
            pending = null,
            playback = PlaybackState.IDLE,
            dialog = AudioBarDialog.None,
            preview = null,
            isTimingAvailable = false,
        )
    }
}
