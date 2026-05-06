package yuku.alkitab.base.audio

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
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
object AudioCatalogRepository {

    private const val TAG = "AudioCatalogRepo"
    private const val OVERRIDE_FILENAME = "audio_catalog.json"
    private const val ASSET_FILENAME = "audio_catalog.json"
    private const val CATALOG_PATH = "/audio/catalog"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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
        val loaded = withContext(Dispatchers.IO) { readFromDiskOrAsset() }
        cached = loaded
        return loaded
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
                    200 -> {
                        val body = response.body?.string()
                            ?: return@use RefreshResult.Failed("empty body")
                        // Validate by parsing before we overwrite the on-disk override.
                        val parsed = parseOrNull(body)
                            ?: return@use RefreshResult.Failed("body did not parse")
                        writeOverride(body)
                        val newEtag = response.header("ETag")
                        if (newEtag != null) {
                            Preferences.setString(Prefkey.audioCatalog_etag, newEtag)
                        }
                        cached = parsed
                        RefreshResult.Updated(parsed.entries.size)
                    }
                    304 -> RefreshResult.NotModified
                    else -> RefreshResult.Failed("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "catalog refresh failed: ${e.message}")
            RefreshResult.Failed(e.message ?: "I/O error")
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
                val parsed = parseOrNull(overrideFile.readText(Charsets.UTF_8))
                if (parsed != null) return parsed
                AppLog.w(TAG, "override $OVERRIDE_FILENAME failed to parse; falling back to bundled asset")
            } catch (e: IOException) {
                AppLog.w(TAG, "could not read override $OVERRIDE_FILENAME: ${e.message}")
            }
        }

        // 2. Bundled asset.
        try {
            App.context.assets.open(ASSET_FILENAME).use { input ->
                val text = input.bufferedReader(Charsets.UTF_8).readText()
                val parsed = parseOrNull(text)
                if (parsed != null) return parsed
                AppLog.e(TAG, "bundled $ASSET_FILENAME failed to parse — shipping a broken JSON?")
            }
        } catch (e: IOException) {
            AppLog.e(TAG, "could not read bundled $ASSET_FILENAME: ${e.message}")
        }

        // 3. Empty catalog — every toolbar audio icon will be hidden.
        return AudioCatalog()
    }

    private fun parseOrNull(body: String): AudioCatalog? {
        return try {
            json.decodeFromString(AudioCatalog.serializer(), body)
        } catch (e: SerializationException) {
            AppLog.w(TAG, "catalog JSON did not parse: ${e.message}")
            null
        }
    }

    private fun overrideFile(): File {
        return File(App.context.filesDir, OVERRIDE_FILENAME)
    }

    private fun writeOverride(body: String) {
        try {
            overrideFile().writeText(body, Charsets.UTF_8)
        } catch (e: IOException) {
            AppLog.w(TAG, "could not persist refreshed catalog: ${e.message}")
        }
    }
}
