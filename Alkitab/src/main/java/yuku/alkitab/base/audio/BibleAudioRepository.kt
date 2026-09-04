package yuku.alkitab.base.audio

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import yuku.alkitab.base.App
import yuku.alkitab.base.audio.model.AudioSet
import yuku.alkitab.base.audio.builtin.BuiltInAudioCatalog
import yuku.alkitab.base.audio.download.AudioChapterDownloadStore
import yuku.alkitab.base.audio.model.ChapterTiming
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig

/**
 * Audio data layer: turns a `(versionId, audioId, bookId, chapter_1)` address
 * into a chapter MP3 URL and its verse timing.
 *
 * Both methods resolve the recording via [AudioSetsRepository] and expand the
 * URL templates the backend serves for it. The templates exist precisely so
 * the client never hard-codes upstream URLs: adding a recording, or moving
 * the CDN, is a backend-side change.
 *
 * Book and chapter are 1-based in every audio URL, matching the backend and
 * its upstream. The app's `Ari` bookId is 0-based, so the `book_1 = bookId + 1`
 * conversion happens in [expandTemplate], once and nowhere else.
 */
object BibleAudioRepository {

    private const val TAG = "BibleAudioRepo"

    private val json = Json { ignoreUnknownKeys = true }

    internal var http: AudioHttp = OkHttpAudioHttp
    internal var downloadStoreProvider: () -> AudioChapterDownloadStore = {
        AudioChapterDownloadStore(App.context)
    }

    /**
     * Resolves the absolute chapter MP3 URL for
     * `(versionId, audioId, bookId, chapter_1)`, or null when [versionId] has
     * no recording with [audioId], or the recording's template does not resolve
     * to a URL. On the missing-recording path the caller should never have
     * asked: toolbar-icon visibility is gated on the resolved set list.
     */
    suspend fun buildChapterUrl(versionId: String, audioId: String, bookId: Int, chapter_1: Int): String? {
        val set = resolveSet(versionId, audioId) ?: return null
        if (set.mp3UrlTemplate == BuiltInAudioCatalog.LOCATOR) {
            downloadStoreProvider().localUri(audioId, bookId, chapter_1)?.let { return it.toString() }
            return AudioSetsRepository.builtInCatalogProvider().chapterUrl(audioId, bookId, chapter_1)
        }
        return expandTemplate(set.mp3UrlTemplate, bookId, chapter_1)
    }

    /**
     * Fetches the verse timing for `(versionId, audioId, bookId, chapter_1)`.
     * Returns the parsed [ChapterTiming] on success (with a possibly-empty
     * `verses` list when the chapter has audio but no upstream timing), or
     * null on:
     *  - no recording with [audioId] for [versionId],
     *  - a recording without timing (`timingUrlTemplate` is null, so the
     *    request is skipped entirely),
     *  - a `timingUrlTemplate` that does not resolve to a URL,
     *  - any non-2xx response or I/O error,
     *  - JSON parse failure.
     *
     * The 50 MB OkHttp disk cache takes care of per-URL caching (TTLs are set
     * server-side via `Cache-Control`).
     */
    suspend fun fetchTiming(versionId: String, audioId: String, bookId: Int, chapter_1: Int): ChapterTiming? {
        val set = resolveSet(versionId, audioId) ?: return null
        val template = set.timingUrlTemplate ?: return null
        val url = expandTemplate(template, bookId, chapter_1) ?: return null
        val body = http.getBody(url) ?: return null
        parseTiming(body, url)?.let { return it }
        // The unparseable bytes may be a corrupted entry served from the HTTP
        // disk cache. Fetch once past the cache, which also replaces the
        // entry, and give the fresh payload a parse.
        AppLog.w(TAG, "timing did not parse for $url; refetching past the HTTP cache")
        val fresh = http.getBodyRevalidating(url) ?: return null
        return parseTiming(fresh, url)
    }

    private fun parseTiming(body: String, url: String): ChapterTiming? {
        return try {
            json.decodeFromString(ChapterTiming.serializer(), body)
        } catch (e: SerializationException) {
            AppLog.w(TAG, "timing JSON did not parse for $url: ${e.message}")
            null
        }
    }

    private suspend fun resolveSet(versionId: String, audioId: String): AudioSet? =
        AudioSetsRepository.setsFor(versionId).sets.firstOrNull { it.audioId == audioId }

    private val serverBase by lazy { BuildConfig.SERVER_HOST.toHttpUrl() }

    /**
     * Substitutes the placeholders in [template] and resolves the result
     * against [serverBase] the way a browser resolves an href: a path-absolute
     * template lands on the backend host, an absolute URL is taken as it
     * stands. Null when the expansion is not a resolvable URL reference.
     */
    private fun expandTemplate(template: String, bookId: Int, chapter_1: Int): String? {
        val book_1 = bookId + 1
        val expanded = template
            .replace("{book_1}", book_1.toString())
            .replace("{chapter_1}", chapter_1.toString())
        val resolved = serverBase.resolve(expanded)
        if (resolved == null) {
            AppLog.w(TAG, "audio URL template did not resolve: $expanded")
            return null
        }
        return resolved.toString()
    }

    internal fun resetForTest() {
        http = OkHttpAudioHttp
        downloadStoreProvider = { AudioChapterDownloadStore(App.context) }
    }
}
