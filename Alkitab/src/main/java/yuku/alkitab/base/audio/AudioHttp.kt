package yuku.alkitab.base.audio

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.util.AppLog

/**
 * HTTP boundary for the audio endpoints. [AudioSetsRepository] and
 * [BibleAudioRepository] hold a replaceable reference to this, so unit tests
 * can drive the repositories with canned responses instead of a live server.
 */
internal fun interface AudioHttp {
    /**
     * Issues a GET for [url] and returns the response body on a successful
     * (2xx) response, or null on any other outcome (non-2xx, I/O error).
     * Never throws.
     */
    suspend fun getBody(url: String): String?
}

/**
 * Production [AudioHttp] over [Connections.okHttp]. The client's 50 MB disk
 * cache handles revalidation from the response headers (`ETag`,
 * `Cache-Control`) — the app does no manual conditional-request bookkeeping.
 */
internal object OkHttpAudioHttp : AudioHttp {

    private const val TAG = "OkHttpAudioHttp"

    override suspend fun getBody(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        try {
            Connections.okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w(TAG, "GET $url failed: HTTP ${response.code}")
                    return@use null
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "GET $url failed: ${e.message}")
            null
        }
    }
}
