package yuku.alkitab.base.search.theme.pack

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
import yuku.alkitab.base.connection.Connections

internal fun interface ModelPackHttpClient {
    suspend fun execute(url: String, offset: Long): ModelPackHttpResponse
}

internal class ModelPackHttpResponse(
    val code: Int,
    val finalUrl: String,
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

private object OkHttpModelPackClient : ModelPackHttpClient {
    override suspend fun execute(url: String, offset: Long): ModelPackHttpResponse {
        val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
        if (offset > 0) builder.header("Range", "bytes=$offset-")
        val response = Connections.okHttp.newCall(builder.build()).execute()
        return ModelPackHttpResponse(
            code = response.code,
            finalUrl = response.request.url.toString(),
            body = response.body.byteStream(),
            closeAction = response::close,
        )
    }
}

/** Resumably downloads both immutable files, then publishes them as one verified pack. */
class ModelPackDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val manifest = try {
            manifestProvider(applicationContext)
        } catch (_: Exception) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "manifest"))
        }
        val store = storeProvider(applicationContext)
        if (store.inspect(manifest) is ModelPackState.Ready) return@withContext Result.success()

        val total = manifest.model.length + manifest.tokenizer.length
        try {
            var completedBefore = 0L
            for (artifact in listOf(manifest.model, manifest.tokenizer)) {
                downloadArtifact(store, manifest, artifact, completedBefore, total)
                completedBefore += artifact.length
            }
            store.publish(manifest)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            store.discardStaging(manifest)
            Result.failure(workDataOf(KEY_ERROR to "source"))
        } catch (e: IllegalArgumentException) {
            store.discardStaging(manifest)
            Result.failure(workDataOf(KEY_ERROR to "integrity"))
        } catch (_: Exception) {
            // Keep partial bytes so WorkManager retry can continue with Range.
            Result.retry()
        }
    }

    private suspend fun downloadArtifact(
        store: ModelPackStore,
        manifest: ModelPackManifest,
        artifact: ModelPackArtifact,
        completedBefore: Long,
        total: Long,
    ) {
        if (!isAllowedUrl(artifact.url)) throw SecurityException("Untrusted model source")
        val stage = store.stagingFile(manifest, artifact)
        check(stage.parentFile!!.mkdirs() || stage.parentFile!!.isDirectory)
        if (stage.length() > artifact.length) stage.delete()
        var offset = stage.length()

        httpClient.execute(artifact.url, offset).use { response ->
            if (!isAllowedUrl(response.finalUrl)) throw SecurityException("Untrusted redirect")
            val append = offset > 0 && response.code == HTTP_PARTIAL
            if (response.code == HTTP_RANGE_NOT_SATISFIABLE && offset == artifact.length) return
            if (response.code !in 200..299) error("HTTP ${response.code}")
            if (!append) {
                offset = 0
                stage.delete()
            }
            java.io.FileOutputStream(stage, append).buffered().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var current = offset
                setProgress(workDataOf(KEY_BYTES to completedBefore + current, KEY_TOTAL to total))
                while (true) {
                    if (isStopped) error("stopped")
                    val count = response.body.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    current += count
                    if (current > artifact.length) throw IllegalArgumentException("Artifact too large")
                    setProgress(workDataOf(KEY_BYTES to completedBefore + current, KEY_TOTAL to total))
                }
            }
        }
        if (stage.length() != artifact.length) error("Incomplete artifact")
    }

    companion object {
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"

        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val BUFFER_SIZE = 128 * 1024

        internal var httpClient: ModelPackHttpClient = OkHttpModelPackClient
        internal var storeProvider: (Context) -> ModelPackStore = ::ModelPackStore
        internal var manifestProvider: (Context) -> ModelPackManifest = ModelPackManifest::load

        internal fun isAllowedUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (!parsed.isHttps) return false
            val host = parsed.host.lowercase()
            return host == "huggingface.co" || host.endsWith(".huggingface.co") ||
                host == "hf.co" || host.endsWith(".hf.co") ||
                host == "xethub.hf.co" || host.endsWith(".xethub.hf.co")
        }

        internal fun resetForTest() {
            httpClient = OkHttpModelPackClient
            storeProvider = ::ModelPackStore
            manifestProvider = ModelPackManifest::load
        }
    }
}
