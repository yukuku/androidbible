package yuku.alkitab.base.util

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Integration with the separate "Alkitab GPT" app (an AI Bible-study companion by SABDA),
 * reachable from the verse action mode when the user already has it installed.
 *
 * The intent is deliberately flagless: `ChatPopUpActivity` is translucent and finishes itself in
 * `onPause`, so it is meant to sit on top of the calling app's task.
 */
object AlkitabGptIntegration {
    const val PACKAGE_NAME = "org.sabda.gpt"

    const val ACTION_SHOW_CHAT_POPUP = "org.sabda.gpt.action.SHOW_CHAT_POPUP"

    /** Keys the popup's header colors, its logo, and the chat thread prefix, so it must match exactly. */
    const val SOURCE = "Apps Alkitab"

    const val EXTRA_BOOK_NAME = "bookName"
    const val EXTRA_CHAPTER = "chapter"
    const val EXTRA_VERSE_START = "verseStart"
    const val EXTRA_VERSE_END = "verseEnd"
    const val EXTRA_SOURCE = "source"

    suspend fun isChatPopupAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        chatPopupIntent().resolveActivity(context.packageManager) != null
    }

    fun chatPopupIntent(bookName: String, chapter_1: Int, verseStart_1: Int, verseEnd_1: Int): Intent =
        chatPopupIntent()
            .putExtra(EXTRA_BOOK_NAME, bookName)
            .putExtra(EXTRA_CHAPTER, chapter_1)
            .putExtra(EXTRA_VERSE_START, verseStart_1)
            .putExtra(EXTRA_VERSE_END, verseEnd_1)
            .putExtra(EXTRA_SOURCE, SOURCE)

    private fun chatPopupIntent() = Intent(ACTION_SHOW_CHAT_POPUP).setPackage(PACKAGE_NAME)
}
