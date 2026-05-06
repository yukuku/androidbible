package yuku.alkitab.base.audio

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Request
import yuku.alkitab.base.audio.model.ChapterTiming
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig

/**
 * Audio data layer: turns a `(versionId, bookId, chapter_1)` triple into a chapter
 * MP3 URL and its verse timing.
 *
 * Both methods consult [AudioCatalogRepository] for the per-version URL templates
 * served by the backend. The templates live in the catalog precisely so the
 * client never hard-codes upstream URLs — adding a new version, or moving the
 * CDN, is a one-line backend deploy.
 *
 * No upstream folder names, no book-code tables, no URL-construction tricks live
 * in this client. See `docs/features/audio-bible/backend-plan.md` §4.3 for the
 * server-side adapter that handles all of that.
 */
object BibleAudioRepository {

    private const val TAG = "BibleAudioRepo"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Resolves the absolute chapter MP3 URL for `(versionId, bookId, chapter_1)`,
     * or null if the catalog has no entry for [versionId] (which means the
     * caller should never have asked — toolbar icon visibility is gated on
     * [AudioCatalogRepository.isAudioAvailable]).
     *
     * The returned URL points at `${BuildConfig.SERVER_HOST}` rather than at the
     * upstream CDN — the backend then issues a `302 Found` to the real file.
     */
    suspend fun buildChapterUrl(versionId: String, bookId: Int, chapter_1: Int): String? {
        AudioCatalogRepository.loadCatalog()
        val entry = AudioCatalogRepository.findEntry(versionId) ?: return null
        return BuildConfig.SERVER_HOST + entry.chapterUrlTemplate
            .replace("{bookId}", bookId.toString())
            .replace("{chapter_1}", chapter_1.toString())
    }

    /**
     * Fetches the verse timing JSON for `(versionId, bookId, chapter_1)`. Returns
     * the parsed [ChapterTiming] on success (with a possibly-empty `verses`
     * list when the backend reports the chapter has audio but no upstream
     * timing), or null on:
     *  - missing catalog entry,
     *  - missing `timingUrlTemplate` for the entry,
     *  - any non-2xx response,
     *  - I/O error,
     *  - JSON parse failure.
     *
     * I/O happens on [Dispatchers.IO]. The 50 MB OkHttp disk cache on
     * [Connections.okHttp] takes care of per-URL caching (TTLs are set
     * server-side via `Cache-Control`).
     */
    suspend fun fetchTiming(versionId: String, bookId: Int, chapter_1: Int): ChapterTiming? {
        AudioCatalogRepository.loadCatalog()
        val entry = AudioCatalogRepository.findEntry(versionId) ?: return null
        val template = entry.timingUrlTemplate ?: return null
        val url = BuildConfig.SERVER_HOST + template
            .replace("{bookId}", bookId.toString())
            .replace("{chapter_1}", chapter_1.toString())

        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            try {
                Connections.okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLog.w(TAG, "timing fetch failed: HTTP ${response.code} for $url")
                        return@use null
                    }
                    val body = response.body ?: return@use null
                    try {
                        json.decodeFromString(ChapterTiming.serializer(), body.string())
                    } catch (e: SerializationException) {
                        AppLog.w(TAG, "timing JSON did not parse for $url: ${e.message}")
                        null
                    }
                }
            } catch (e: IOException) {
                AppLog.w(TAG, "timing I/O failed for $url: ${e.message}")
                null
            }
        }
    }
}
