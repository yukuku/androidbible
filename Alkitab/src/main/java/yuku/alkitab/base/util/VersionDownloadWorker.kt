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
 * application layer) to a temp file under [Context.getCacheDir]. Always
 * starts from byte 0 — any stale bytes already at the destination path
 * are discarded before the request goes out.
 *
 * Note: we deliberately do *not* support HTTP Range-based resume. The temp
 * file path is derived from the download key (preset name), not from the
 * server's file contents, so a leftover partial could be from a different
 * version (e.g. the user's app was updated, or the server's source file
 * changed). Resuming would risk producing a Frankenstein of bytes from two
 * different files. A failed download just retries from byte 0.
 *
 * Progress is reported via [setProgress] as the pair (current, total) of
 * raw on-disk bytes. If the response has no Content-Length (e.g. chunked
 * transfer), total is -1 and the caller should render an indeterminate
 * progress bar. The worker requests `Accept-Encoding: identity` so the
 * byte counts on the wire match the bytes on disk — without this, OkHttp
 * would transparently decode any `Content-Encoding: gzip` response and
 * strip Content-Length, breaking progress reporting.
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
        // Discard any stale bytes from a previous attempt: see the class
        // KDoc — we don't trust them to belong to the current server file.
        if (destFile.exists()) {
            destFile.delete()
        }

        val response = try {
            Connections.okHttp.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("Accept-Encoding", "identity")
                    .build()
            ).execute()
        } catch (e: IOException) {
            return@withContext Result.failure(failureData(ERROR_CONNECTION, e.message ?: e.javaClass.simpleName))
        } catch (e: Exception) {
            return@withContext Result.failure(failureData(ERROR_UNKNOWN, e.message ?: e.javaClass.simpleName))
        }

        response.use {
            if (!response.isSuccessful) {
                val errorType = if (response.code in 500..599) ERROR_SERVER else ERROR_CONNECTION
                return@withContext Result.failure(failureData(errorType, "HTTP ${response.code}"))
            }
            val body = response.body
                ?: return@withContext Result.failure(failureData(ERROR_CONNECTION, "response body is null"))

            val totalBytes = body.contentLength()  // -1 if unknown (chunked)

            val raf = try {
                RandomAccessFile(destFile, "rw")
            } catch (e: IOException) {
                return@withContext Result.failure(failureData(ERROR_STORAGE, e.message ?: e.javaClass.simpleName))
            }

            raf.use {
                body.byteStream().use { input ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var currentBytes = 0L
                    var lastProgressTime = 0L

                    setProgress(workDataOf(
                        KEY_CURRENT_BYTES to currentBytes,
                        KEY_TOTAL_BYTES to totalBytes,
                    ))

                    while (true) {
                        if (isStopped) {
                            return@withContext Result.failure(failureData(ERROR_CANCELLED, "cancelled"))
                        }
                        val read = try {
                            input.read(buf)
                        } catch (e: IOException) {
                            return@withContext Result.failure(failureData(ERROR_CONNECTION, e.message ?: e.javaClass.simpleName))
                        }
                        if (read < 0) break
                        try {
                            raf.write(buf, 0, read)
                        } catch (e: IOException) {
                            return@withContext Result.failure(failureData(ERROR_STORAGE, e.message ?: e.javaClass.simpleName))
                        }
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
        const val ERROR_STORAGE = "storage"
        const val ERROR_CANCELLED = "cancelled"
        const val ERROR_UNKNOWN = "unknown"

        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_THROTTLE_MS = 100L
    }
}
