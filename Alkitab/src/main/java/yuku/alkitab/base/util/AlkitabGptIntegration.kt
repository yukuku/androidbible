package yuku.alkitab.base.util

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Integration with the separate "Alkitab GPT" app (an AI Bible-study companion by SABDA),
 * reachable from the verse action mode when the user already has it installed.
 *
 * Alkitab GPT exposes `ChatPopUpActivity` under [ACTION_SHOW_CHAT_POPUP] and reads the verse
 * context from the extras below (its `ChatContextFactory`). The intent is deliberately left
 * flagless: that activity uses a translucent theme and finishes itself in `onPause`, so it is
 * meant to appear as a popup on top of the calling app's task.
 */
object AlkitabGptIntegration {
    const val PACKAGE_NAME = "org.sabda.gpt"

    const val ACTION_SHOW_CHAT_POPUP = "org.sabda.gpt.action.SHOW_CHAT_POPUP"

    /**
     * The host-app name Alkitab GPT recognises as this app. It keys the popup's header colors,
     * its logo, and the `apps_alkitab` prefix on the chat thread, so it has to match exactly.
     */
    const val SOURCE = "Apps Alkitab"

    const val EXTRA_BOOK_NAME = "bookName"
    const val EXTRA_CHAPTER = "chapter"
    const val EXTRA_VERSE_START = "verseStart"
    const val EXTRA_VERSE_END = "verseEnd"
    const val EXTRA_SOURCE = "source"

    /**
     * Whether Alkitab GPT is installed and new enough to accept a chat-popup request.
     *
     * PackageManager lookups are binder calls into system_server and can stall for a noticeable
     * time on a loaded device, so this suspends on [Dispatchers.IO] instead of blocking the
     * caller's thread.
     */
    suspend fun isChatPopupAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        chatPopupIntent().resolveActivity(context.packageManager) != null
    }

    /**
     * A chat-popup request for the given passage. [verseStart_1] and [verseEnd_1] are the ends of
     * the selection: Alkitab GPT renders them as "Kejadian 1:1-3", collapsing to a single verse
     * when they are equal.
     */
    fun chatPopupIntent(bookName: String, chapter_1: Int, verseStart_1: Int, verseEnd_1: Int): Intent =
        chatPopupIntent()
            .putExtra(EXTRA_BOOK_NAME, bookName)
            .putExtra(EXTRA_CHAPTER, chapter_1)
            .putExtra(EXTRA_VERSE_START, verseStart_1)
            .putExtra(EXTRA_VERSE_END, verseEnd_1)
            .putExtra(EXTRA_SOURCE, SOURCE)

    private fun chatPopupIntent() = Intent(ACTION_SHOW_CHAT_POPUP).setPackage(PACKAGE_NAME)
}
