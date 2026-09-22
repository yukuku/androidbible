package yuku.alkitab.base.audio

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import yuku.alkitab.base.audio.model.AudioSet
import yuku.alkitab.base.audio.model.ChapterTiming
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig

/**
 * Audio data layer: turns a `(versionId, audioId, bookId, chapter_1)` address
 * into a chapter MP3 URL and its verse timing.
 *
 * URLs come from templates the backend serves, so adding a recording or moving
 * the CDN stays a backend-side change.
 *
 * Book and chapter are 1-based in every audio URL, while the app's `Ari` bookId
 * is 0-based, so the conversion happens in [expandTemplate] and nowhere else.
 */
object BibleAudioRepository {

    private const val TAG = "BibleAudioRepo"

    private val json = Json { ignoreUnknownKeys = true }

    internal var http: AudioHttp = OkHttpAudioHttp

    /**
     * Returns null when [versionId] has no recording with [audioId]. Callers
     * should not hit that case, because toolbar-icon visibility is gated on the
     * resolved set list.
     */
    suspend fun buildChapterUrl(versionId: String, audioId: String, bookId: Int, chapter_1: Int): String? {
        val set = resolveSet(versionId, audioId) ?: return null
        return expandTemplate(set.mp3UrlTemplate, bookId, chapter_1)
    }

    /**
     * Returns null when there is no timing to fetch, including for a recording
     * that has none. A returned [ChapterTiming] may still carry an empty
     * `verses` list, for a chapter with audio but no upstream timing. Per-URL
     * caching is handled by the OkHttp disk cache, with TTLs set server-side.
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
     * Resolves against [serverBase] the way a browser resolves an href, so a
     * path-absolute template lands on the backend host and an absolute URL is
     * taken as it stands.
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
}
