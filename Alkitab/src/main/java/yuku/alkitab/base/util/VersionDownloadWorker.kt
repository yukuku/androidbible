package yuku.alkitab.base.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import yuku.alkitab.base.connection.Connections

/**
 * Downloads a Bible version (.yes file, possibly gzip-compressed at the
 * application layer) to a temp file under [Context.getCacheDir]. Supports
 * HTTP Range-based resume: if the destination file already has bytes, sends
 * `Range: bytes=N-` and appends the response body to it. Falls back to a
 * full restart if the server answers 200 instead of 206 Partial Content.
 *
 * Progress is reported via [setProgress] as the pair (current, total) of
 * raw on-disk bytes. If the response has no Content-Length (e.g. chunked
 * transfer), total is -1 and the caller should render an indeterminate
 * progress bar. The worker requests `Accept-Encoding: identity` so the
 * byte counts on the wire match the bytes on disk — without this, OkHttp
 * would transparently decode any `Content-Encoding: gzip` response and
 * strip Content-Length, breaking both progress reporting and Range-based
 * resume (the server's byte offsets and our on-disk offsets would diverge).
 *
 * The worker only performs the byte transfer; post-processing (gunzip,
 * validate via YesReaderFactory, register in DB) is handled by
 * [yuku.alkitab.base.br.VersionDownloadCompleteReceiver] once
 * [DownloadMapper] observes [androidx.work.WorkInfo.State.SUCCEEDED].
 */
class VersionDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL)
            ?: return@withContext Result.failure(failureData(ERROR_UNKNOWN, "missing url"))
        val destPath = inputData.getString(KEY_DEST_PATH)
            ?: return@withContext Result.failure(failureData(ERROR_UNKNOWN, "missing destPath"))

        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()
        val existingBytes = if (destFile.exists()) destFile.length() else 0L

        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Accept-Encoding", "identity")
            if (existingBytes > 0L) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            Connections.okHttp.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorType = if (response.code in 500..599) ERROR_SERVER else ERROR_CONNECTION
                    return@withContext Result.failure(failureData(errorType, "HTTP ${response.code}"))
                }
                val body = response.body ?: throw IOException("response body is null")

                val appending = response.code == 206 && existingBytes > 0L
                val startOffset = if (appending) existingBytes else 0L
                if (!appending && destFile.exists()) {
                    destFile.delete()
                }

                val responseBodyLength = body.contentLength()
                val totalBytes = when {
                    responseBodyLength < 0L -> -1L
                    appending -> startOffset + responseBodyLength
                    else -> responseBodyLength
                }

                RandomAccessFile(destFile, "rw").use { raf ->
                    raf.seek(startOffset)

                    body.byteStream().use { input ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var currentBytes = startOffset
                        var lastProgressTime = 0L

                        setProgress(workDataOf(
                            KEY_CURRENT_BYTES to currentBytes,
                            KEY_TOTAL_BYTES to totalBytes,
                        ))

                        while (true) {
                            if (isStopped) {
                                return@withContext Result.failure(failureData(ERROR_CANCELLED, "cancelled"))
                            }
                            val read = input.read(buf)
                            if (read < 0) break
                            raf.write(buf, 0, read)
                            currentBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime > PROGRESS_THROTTLE_MS) {
                                lastProgressTime = now
                                setProgress(workDataOf(
                                    KEY_CURRENT_BYTES to currentBytes,
                                    KEY_TOTAL_BYTES to totalBytes,
                                ))
                            }
                        }
                    }
                }
            }

            Result.success(workDataOf(KEY_DEST_PATH to destPath))
        } catch (e: IOException) {
            Result.failure(failureData(ERROR_CONNECTION, e.message ?: e.javaClass.simpleName))
        } catch (e: Exception) {
            Result.failure(failureData(ERROR_UNKNOWN, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun failureData(errorType: String, message: String): Data = workDataOf(
        KEY_ERROR_TYPE to errorType,
        KEY_ERROR_MESSAGE to message,
    )

    companion object {
        const val KEY_URL = "url"
        const val KEY_DEST_PATH = "destPath"
        const val KEY_CURRENT_BYTES = "currentBytes"
        const val KEY_TOTAL_BYTES = "totalBytes"
        const val KEY_ERROR_TYPE = "errorType"
        const val KEY_ERROR_MESSAGE = "errorMessage"

        const val ERROR_CONNECTION = "connection"
        const val ERROR_SERVER = "server"
        const val ERROR_CANCELLED = "cancelled"
        const val ERROR_UNKNOWN = "unknown"

        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_THROTTLE_MS = 100L
    }
}
