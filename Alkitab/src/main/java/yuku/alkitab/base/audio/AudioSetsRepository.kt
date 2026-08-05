package yuku.alkitab.base.audio

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import yuku.alkitab.base.App
import yuku.alkitab.base.audio.model.AudioSet
import yuku.alkitab.base.audio.model.AudioSets
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig

/**
 * Resolves which audio recordings ([AudioSet]s) exist for one Bible version,
 * one version at a time, via the backend's `GET /audio/sets/<preset>`.
 *
 * - **Version → preset name.** Every [MVersion] subtype carries a
 *   `preset_name` (exposed uniformly through [MVersion.getPresetName]),
 *   including the internal version, whose value comes from the per-flavor
 *   `BuildConfig.INTERNAL_VERSION_PRESET_NAME`. A version without a preset
 *   name (a `file/…` version, or an internal version whose flavor declares
 *   none) short-circuits to an empty set list without a network request.
 * - **In-memory cache** keyed by versionId, holding negative results too — a
 *   version with no audio must not re-query on every chapter turn. Network
 *   and parse failures also resolve (and cache) as an empty set list, per the
 *   backend contract's error-handling table: the entry point stays hidden
 *   rather than showing a dead button, and there is no retry loop.
 * - **Disk cache** is the existing 50 MB OkHttp cache on
 *   `Connections.okHttp`, honoring the backend's `Cache-Control` — no
 *   app-managed file, no bundled asset, no hand-rolled ETag bookkeeping.
 * - Concurrent [setsFor] calls for the same version share a single request.
 *
 * Thread safety: every public method is safe to call from any thread.
 */
object AudioSetsRepository {

    private const val TAG = "AudioSetsRepo"
    private const val SETS_PATH = "/audio/sets/"

    // ignoreUnknownKeys tolerates additive fields (e.g. `generatedAt`), but the
    // parse stays strict about missing fields: the models declare no default
    // parameter values, so a backend contract change fails loudly here instead
    // of yielding a silently-empty model.
    private val json = Json { ignoreUnknownKeys = true }

    /** Resolves a versionId to the preset name its audio is published under. */
    internal fun interface PresetNameResolver {
        fun presetNameFor(versionId: String): String?
    }

    private val defaultPresetNameResolver = PresetNameResolver { versionId ->
        presetNameFor(versionId, App.services.versions)
    }
    private val defaultHttp: AudioHttp = OkHttpAudioHttp

    internal var presetNameResolver: PresetNameResolver = defaultPresetNameResolver
    internal var http: AudioHttp = defaultHttp

    private val cache = ConcurrentHashMap<String, AudioSets>()

    /**
     * In-flight requests keyed by versionId, so concurrent [setsFor] callers
     * for one version issue a single network request. Guarded by [mutex].
     */
    private val inFlight = HashMap<String, Deferred<AudioSets>>()
    private val mutex = Mutex()

    /**
     * Fetches run in a repository-owned scope so a caller's cancellation
     * (e.g. an activity going away mid-menu-preparation) doesn't abort a
     * request other callers are awaiting.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Returns the audio sets for [versionId], from the in-memory cache when
     * resolved before, otherwise fetching from the backend. Never throws — any
     * failure resolves to an empty set list, which is cached like any other
     * answer.
     */
    suspend fun setsFor(versionId: String): AudioSets {
        cache[versionId]?.let { return it }
        val deferred = mutex.withLock {
            cache[versionId]?.let { return it }
            inFlight.getOrPut(versionId) {
                scope.async {
                    val result = fetchSets(versionId)
                    cache[versionId] = result
                    mutex.withLock { inFlight.remove(versionId) }
                    result
                }
            }
        }
        return deferred.await()
    }

    /**
     * Non-blocking peek at the in-memory cache. Null means [versionId] has not
     * been resolved yet this process — callers (menu preparation) hide the
     * entry point and kick off [setsFor], re-preparing once it lands.
     */
    fun cachedSetsFor(versionId: String): AudioSets? = cache[versionId]

    private suspend fun fetchSets(versionId: String): AudioSets {
        val presetName = presetNameResolver.presetNameFor(versionId)
            ?: return emptySets("")
        val body = http.getBody(BuildConfig.SERVER_HOST + SETS_PATH + presetName)
            ?: return emptySets(presetName)
        return try {
            json.decodeFromString(AudioSets.serializer(), body)
        } catch (e: SerializationException) {
            AppLog.w(TAG, "audio sets JSON did not parse for $presetName: ${e.message}")
            emptySets(presetName)
        }
    }

    /** Synthesized "no audio" answer; `schema = 0` marks it as client-made. */
    private fun emptySets(preset: String) = AudioSets(schema = 0, preset = preset, sets = emptyList())

    /**
     * Reads a version's preset name uniformly through [MVersion.getPresetName],
     * whatever the subtype. [VersionManager.getVersionFromVersionId] returns
     * null for the internal version by contract, so that id is mapped to the
     * internal [MVersion] first; the preset read itself has no per-subtype
     * branching.
     */
    internal fun presetNameFor(versionId: String, versions: VersionManager): String? {
        val mv: MVersion? = if (versionId == MVersionInternal.getVersionInternalId()) {
            versions.getMVersionInternal()
        } else {
            versions.getVersionFromVersionId(versionId)
        }
        val presetName = mv?.presetName
        return if (presetName.isNullOrEmpty()) null else presetName
    }

    /**
     * Clears all cached and in-flight state and restores the default seams.
     * Only for tests, which run with no concurrent [setsFor] callers.
     */
    internal fun resetForTest() {
        cache.clear()
        inFlight.clear()
        presetNameResolver = defaultPresetNameResolver
        http = defaultHttp
    }
}
