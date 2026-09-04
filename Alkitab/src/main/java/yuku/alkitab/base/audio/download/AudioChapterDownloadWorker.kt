package yuku.alkitab.base.audio.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.Closeable
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import yuku.alkitab.base.audio.builtin.BuiltInAudioCatalog
import yuku.alkitab.base.connection.Connections

fun interface AudioChapterHttpClient {
    suspend fun execute(url: String): AudioChapterHttpResponse
}

class AudioChapterHttpResponse(
    val isSuccessful: Boolean,
    val finalUrl: String,
    val contentType: String?,
    val contentLength: Long,
    val body: InputStream,
    private val closeAction: () -> Unit,
) : Closeable {
    override fun close() {
        try {
            body.close()
        } finally {
            closeAction()
        }
    }
}

private object OkHttpAudioChapterHttpClient : AudioChapterHttpClient {
    override suspend fun execute(url: String): AudioChapterHttpResponse {
        val response = Connections.okHttp.newCall(
            Request.Builder()
                .url(url)
                .header("Accept-Encoding", "identity")
                .build(),
        ).execute()
        val body = response.body
        return AudioChapterHttpResponse(
            isSuccessful = response.isSuccessful,
            finalUrl = response.request.url.toString(),
            contentType = body.contentType()?.toString(),
            contentLength = body.contentLength(),
            body = body.byteStream(),
            closeAction = response::close,
        )
    }
}

/** Downloads one validated AudioTreasure chapter into app-private storage. */
class AudioChapterDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val audioId = inputData.getString(KEY_AUDIO_ID) ?: return@withContext Result.failure()
        val bookId = inputData.getInt(KEY_BOOK_ID, INVALID_COORDINATE)
        val chapter1 = inputData.getInt(KEY_CHAPTER_1, INVALID_COORDINATE)
        val sourceUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        if (audioId != BuiltInAudioCatalog.AUDIO_ID || !isAllowedUrl(sourceUrl)) {
            return@withContext Result.failure()
        }

        val store = storeProvider(applicationContext)
        val temp = try {
            store.tempFile(audioId, bookId, chapter1)
        } catch (_: IllegalArgumentException) {
            return@withContext Result.failure()
        }
        temp.delete()

        var published = false
        try {
            httpClient.execute(sourceUrl).use { response ->
                if (!response.isSuccessful || !isAllowedUrl(response.finalUrl)) {
                    return@withContext Result.failure()
                }
                if (response.contentType?.substringBefore('/')?.lowercase() != "audio") {
                    return@withContext Result.failure()
                }

                temp.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var currentBytes = 0L
                    setProgress(workDataOf(KEY_CURRENT_BYTES to 0L, KEY_TOTAL_BYTES to response.contentLength))
                    while (true) {
                        if (isStopped) return@withContext Result.failure()
                        val count = response.body.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        currentBytes += count
                        setProgress(
                            workDataOf(
                                KEY_CURRENT_BYTES to currentBytes,
                                KEY_TOTAL_BYTES to response.contentLength,
                            ),
                        )
                    }
                }
                store.publishTemp(audioId, bookId, chapter1, temp)
                published = true
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.failure()
        } finally {
            if (!published) temp.delete()
        }
    }

    companion object {
        const val KEY_AUDIO_ID = "audioId"
        const val KEY_BOOK_ID = "bookId"
        const val KEY_CHAPTER_1 = "chapter1"
        const val KEY_URL = "url"
        const val KEY_CURRENT_BYTES = "currentBytes"
        const val KEY_TOTAL_BYTES = "totalBytes"

        private const val ALLOWED_HOST = "audiotreasure.com"
        private const val INVALID_COORDINATE = -1
        private const val BUFFER_SIZE = 32 * 1_024

        internal var httpClient: AudioChapterHttpClient = OkHttpAudioChapterHttpClient
        internal var storeProvider: (Context) -> AudioChapterDownloadStore = ::AudioChapterDownloadStore

        internal fun resetForTest() {
            httpClient = OkHttpAudioChapterHttpClient
            storeProvider = ::AudioChapterDownloadStore
        }

        private fun isAllowedUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            return parsed.isHttps && parsed.host == ALLOWED_HOST
        }
    }
}
