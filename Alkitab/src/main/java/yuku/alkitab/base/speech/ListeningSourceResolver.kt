package yuku.alkitab.base.speech

import yuku.alkitab.base.audio.RecordedAudioAvailability

sealed interface ListeningSource {
    data class Recorded(val audioId: String) : ListeningSource
    data object GoogleTts : ListeningSource
    data class Unavailable(val reason: ListeningUnavailableReason) : ListeningSource
}

enum class ListeningUnavailableReason {
    RECORDED_FAILED,
}

/** The single precedence policy shared by reader and search listening actions. */
class ListeningSourceResolver {
    fun resolve(
        availability: RecordedAudioAvailability,
        explicitTtsAfterFailure: Boolean,
    ): ListeningSource = when (availability) {
        is RecordedAudioAvailability.Available -> ListeningSource.Recorded(availability.audioId)
        RecordedAudioAvailability.Unavailable -> ListeningSource.GoogleTts
        is RecordedAudioAvailability.Failed -> {
            if (explicitTtsAfterFailure) {
                ListeningSource.GoogleTts
            } else {
                ListeningSource.Unavailable(ListeningUnavailableReason.RECORDED_FAILED)
            }
        }
    }
}
