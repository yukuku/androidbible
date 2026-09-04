package yuku.alkitab.base.accessibility

import android.content.Context
import java.util.Locale
import yuku.alkitab.base.audio.RecordedAudioAvailability
import yuku.alkitab.debug.R

/** Centralized, localized phrases used by TalkBack for search and listening. */
class ListeningAccessibilityText(
    private val resultTemplate: String,
    private val recordedAction: String,
    private val ttsAction: String,
    private val failedAction: String,
) {
    fun resultDescription(
        position1: Int,
        total: Int,
        reference: String,
        verseText: String,
    ): String = String.format(
        Locale.getDefault(),
        resultTemplate,
        position1,
        total,
        reference.trim().trimEnd('.', '!', '?'),
        verseText.trim().trimEnd('.', '!', '?'),
    )

    fun listenAction(availability: RecordedAudioAvailability): String = when (availability) {
        is RecordedAudioAvailability.Available -> recordedAction
        RecordedAudioAvailability.Unavailable -> ttsAction
        is RecordedAudioAvailability.Failed -> failedAction
    }

    companion object {
        fun from(context: Context) = ListeningAccessibilityText(
            resultTemplate = context.getString(R.string.search_result_accessibility),
            recordedAction = context.getString(R.string.listen_recorded_accessibility),
            ttsAction = context.getString(R.string.listen_google_tts_accessibility),
            failedAction = context.getString(R.string.listen_failed_accessibility),
        )
    }
}
