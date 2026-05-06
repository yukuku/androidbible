package yuku.alkitab.base.audio

import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Request
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.audio.model.AudioCatalog
import yuku.alkitab.base.audio.model.AudioVersion
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig

/**
 * Source of truth for the audio catalog (which versions have audio available + the
 * URL templates the client uses to fetch chapter MP3s and timing JSON).
 *
 * Catalog precedence on read:
 *   1. In-memory cache (warmed by [loadCatalog]).
 *   2. Override file at `files/audio_catalog.json` (last successful network fetch).
 *   3. Bundled fallback at `assets/audio_catalog.json` (always present, ships with the APK).
 *
 * The repository is also responsible for [refresh] — issuing a conditional GET
 * against `/audio/catalog`, persisting the body and ETag on success, and updating
 * the in-memory cache. Refresh is invoked from
 * [yuku.alkitab.base.sv.VersionConfigUpdaterService] alongside the existing
 * version-config refresh, so the catalog is kept current without spawning a new
 * worker.
 *
 * Thread safety: every public method is safe to call from any thread.
 * [loadCatalog] and [refresh] do their I/O on [Dispatchers.IO]; [findEntry] and
 * [isAudioAvailable] are pure-read operations against the in-memory cache.
 */
@OptIn(ExperimentalSerializationApi::class)
object AudioCatalogRepository {

    private const val TAG = "AudioCatalogRepo"
    private const val OVERRIDE_FILENAME = "audio_catalog.json"
    private const val ASSET_FILENAME = "audio_catalog.json"
    private const val CATALOG_PATH = "/audio/catalog"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Serializes the cache-load critical section in [loadCatalog] so concurrent
     * first-callers don't both read+parse the override/asset, and serializes the
     * cached-write side of [refresh] so a reader never sees a torn `cached` ref.
     * The actual override-file write in [refresh] does **not** sit under this
     * lock — it goes via write-to-temp + atomic rename, which is itself
     * crash-safe and lock-free against concurrent reads.
     */
    private val loadMutex = Mutex()

    @Volatile
    private var cached: AudioCatalog? = null

    /**
     * Returns the catalog, reading from the override file or the bundled asset on
     * first call. Subsequent calls return the in-memory cache. Never throws — on
     * any error, returns an empty [AudioCatalog] (the toolbar icon will then be
     * hidden everywhere).
     */
    suspend fun loadCatalog(): AudioCatalog {
        cached?.let { return it }
        return loadMutex.withLock {
            // Re-check inside the lock: another coroutine may have raced ahead
            // while we were waiting and already populated `cached`.
            cached?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) { readFromDiskOrAsset() }
            cached = loaded
            loaded
        }
    }

    /** True iff [versionId] has an entry in the currently-loaded catalog. */
    fun isAudioAvailable(versionId: String?): Boolean {
        return findEntry(versionId) != null
    }

    /**
     * Returns the catalog entry for [versionId], or null if the catalog hasn't
     * been loaded yet or the version isn't covered.
     *
     * If [versionId] is the internal Bible version (`"internal"`, see
     * [MVersionInternal.getVersionInternalId]), the lookup is redirected to the
     * flavor-specific audio identifier from `BuildConfig.INTERNAL_VERSION_AUDIO_ID`
     * — e.g. `yuku_alkitab` and `sabda_alkitab` map to `preset/in-tb`,
     * `yuku_quick_bible` maps to `preset/en-kjv`. This lets users get audio for
     * the bundled internal version even though it doesn't ship under a
     * preset versionId of its own.
     */
    fun findEntry(versionId: String?): AudioVersion? {
        val effective = effectiveVersionId(versionId) ?: return null
        return cached?.entries?.firstOrNull { it.versionId == effective }
    }

    private fun effectiveVersionId(versionId: String?): String? {
        if (versionId.isNullOrEmpty()) return null
        if (versionId != MVersionInternal.getVersionInternalId()) return versionId
        val mapped = BuildConfig.INTERNAL_VERSION_AUDIO_ID
        return if (mapped.isEmpty()) null else mapped
    }

    /**
     * Issues `GET /audio/catalog` with `If-None-Match` set to the cached ETag (if
     * any). On 200, persists the body to `files/audio_catalog.json` and stores the
     * new ETag. On 304, leaves the on-disk override untouched. On any other
     * outcome (network error, parse failure, non-2xx, non-304), the override
     * stays as-is.
     *
     * Either way, the in-memory cache is invalidated so the next [loadCatalog]
     * picks up whatever is now on disk.
     */
    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
        val url = BuildConfig.SERVER_HOST + CATALOG_PATH + "?" + App.getAppIdentifierParamsEncoded()
        val etag = Preferences.getString(Prefkey.audioCatalog_etag, null)
        val request = Request.Builder()
            .url(url)
            .apply { if (!etag.isNullOrEmpty()) header("If-None-Match", etag) }
            .build()

        try {
            Connections.okHttp.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> handleOk200(response)
                    304 -> RefreshResult.NotModified
                    else -> RefreshResult.Failed("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "catalog refresh failed: ${e.message}")
            RefreshResult.Failed(e.message ?: "I/O error")
        }
    }

    /**
     * 200 handler:
     *  1. Stream the response body into a unique temp file under filesDir
     *     (avoids holding the full body in memory).
     *  2. Parse-from-stream off the temp file. Bail and delete the temp on parse
     *     failure — the existing override stays intact.
     *  3. On parse success, atomically rename the temp over the override path.
     *     Atomic rename is a single filesystem op on POSIX (which Android is),
     *     so concurrent [loadCatalog] readers see either the old or the new
     *     file but never a partial write.
     *  4. Update the in-memory cache and ETag last.
     */
    private fun handleOk200(response: okhttp3.Response): RefreshResult {
        val body = response.body ?: return RefreshResult.Failed("empty body")

        val temp = File.createTempFile("audio_catalog_", ".tmp.json", App.context.filesDir)
        try {
            body.byteStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }

            val parsed = temp.inputStream().use(::parseStreamOrNull)
                ?: return RefreshResult.Failed("body did not parse")

            // Atomic publish. Some platforms reject rename if the target exists
            // (Android doesn't, but we delete first to be portable to host JVM
            // tests which run under Robolectric on the host filesystem).
            val target = overrideFile()
            target.delete()
            if (!temp.renameTo(target)) {
                return RefreshResult.Failed("could not commit override file")
            }

            response.header("ETag")?.let {
                Preferences.setString(Prefkey.audioCatalog_etag, it)
            }
            cached = parsed
            return RefreshResult.Updated(parsed.entries.size)
        } finally {
            // Best-effort cleanup if the rename never happened.
            if (temp.exists()) temp.delete()
        }
    }

    sealed class RefreshResult {
        data class Updated(val entryCount: Int) : RefreshResult()
        data object NotModified : RefreshResult()
        data class Failed(val reason: String) : RefreshResult()
    }

    /**
     * Synchronous bridge for Java callers (e.g.
     * [yuku.alkitab.base.sv.VersionConfigUpdaterService]). Blocks the calling
     * thread; intended for use from existing IntentService-style worker threads.
     * Not for use on the main thread.
     */
    @JvmStatic
    fun refreshBlocking(): RefreshResult = runBlocking { refresh() }

    // ---------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------

    private fun readFromDiskOrAsset(): AudioCatalog {
        // 1. Override file from a previous network refresh.
        val overrideFile = overrideFile()
        if (overrideFile.isFile) {
            try {
                val parsed = overrideFile.inputStream().use(::parseStreamOrNull)
                if (parsed != null) return parsed
                AppLog.w(TAG, "override $OVERRIDE_FILENAME failed to parse; falling back to bundled asset")
            } catch (e: IOException) {
                AppLog.w(TAG, "could not read override $OVERRIDE_FILENAME: ${e.message}")
            }
        }

        // 2. Bundled asset.
        try {
            App.context.assets.open(ASSET_FILENAME).use { input ->
                val parsed = parseStreamOrNull(input)
                if (parsed != null) return parsed
                AppLog.e(TAG, "bundled $ASSET_FILENAME failed to parse — shipping a broken JSON?")
            }
        } catch (e: IOException) {
            AppLog.e(TAG, "could not read bundled $ASSET_FILENAME: ${e.message}")
        }

        // 3. Empty catalog — every toolbar audio icon will be hidden.
        return AudioCatalog()
    }

    /**
     * Streams the JSON straight off the input — never buffers the whole body
     * into a `String` first. Returns null on parse failure (logged at warn).
     */
    private fun parseStreamOrNull(input: InputStream): AudioCatalog? {
        return try {
            json.decodeFromStream(AudioCatalog.serializer(), input)
        } catch (e: SerializationException) {
            AppLog.w(TAG, "catalog JSON did not parse: ${e.message}")
            null
        }
    }

    private fun overrideFile(): File {
        return File(App.context.filesDir, OVERRIDE_FILENAME)
    }
}
