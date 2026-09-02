package yuku.alkitab.base.util

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Integration with the separate "Alkitab GPT" app (an AI Bible-study companion by SABDA),
 * reachable from the verse action mode when the user already has it installed.
 *
 * Every PackageManager lookup here is a binder round-trip to system_server, which can stall
 * for tens of milliseconds under load, so resolution is a suspending call meant for a
 * background dispatcher. [resolveLaunchIntent] answers both questions the caller has (is it
 * installed, and how do we open it) in one pass, so nothing has to touch PackageManager again
 * on the main thread when the user taps the menu item.
 */
object AlkitabGptIntegration {
    const val PACKAGE_NAME = "org.sabda.gpt"

    /**
     * The verse-lookup action, following the same `org.sabda.<app>.action.VIEW` convention as
     * the Pedia and Tafsiran integrations. Older builds of Alkitab GPT may not declare it; see
     * [resolveLaunchIntent] for the fallback.
     */
    private const val ACTION_VIEW = "org.sabda.gpt.action.VIEW"

    const val EXTRA_ARI = "ari"
    const val EXTRA_REFERENCE = "reference"
    const val EXTRA_VERSE_TEXT = "verseText"

    /**
     * A ready-to-start intent template for Alkitab GPT, or null when the app is not installed.
     *
     * Prefers the verse-lookup action so the app can open straight to the selected verse, and
     * falls back to the launcher entry point when that action is not declared. The fallback
     * still carries the verse extras: an app that does not understand them ignores them, and
     * the user at least lands in Alkitab GPT instead of nowhere.
     *
     * Suspends on [Dispatchers.IO] rather than blocking the caller's thread.
     */
    suspend fun resolveLaunchIntent(context: Context): Intent? = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        val viewIntent = Intent(ACTION_VIEW).setPackage(PACKAGE_NAME)
        if (viewIntent.resolveActivity(pm) != null) {
            return@withContext viewIntent
        }

        pm.getLaunchIntentForPackage(PACKAGE_NAME)
    }

    /**
     * Copies [template] (as returned by [resolveLaunchIntent]) and attaches the selected verse.
     * The template is reused across taps, so it must not be mutated in place.
     */
    fun withVerse(template: Intent, ari: Int, reference: String, verseText: String?): Intent =
        Intent(template)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_ARI, ari)
            .putExtra(EXTRA_REFERENCE, reference)
            .putExtra(EXTRA_VERSE_TEXT, verseText)
}
