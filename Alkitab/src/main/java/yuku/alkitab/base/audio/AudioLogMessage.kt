package yuku.alkitab.base.audio

import android.content.Context
import androidx.annotation.StringRes

/**
 * A log event before it is turned into display text: the string resource for
 * the event plus its format arguments. Producers (the HTTP event listener,
 * the player, the audio bar) emit these; [BibleAudioService] resolves them
 * against its own [Context] when appending to [PlaybackState.logs], so the
 * log reads in the user's language.
 *
 * Keeping the resource id unresolved at the producer means those producers
 * need no [Context] and stay unit-testable by asserting on ids and args.
 */
data class AudioLogMessage(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
) {
    fun resolve(context: Context): String {
        val resolvedArgs = args.map { if (it is AudioLogStateLabel) context.getString(it.resId) else it }
        return context.getString(resId, *resolvedArgs.toTypedArray())
    }
}

/**
 * A format argument that is itself a string resource, for messages that embed
 * a translated word rather than a value. Comparing two labels compares their
 * ids, so callers can tell whether something changed without a [Context].
 */
data class AudioLogStateLabel(@StringRes val resId: Int)
