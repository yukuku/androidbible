package yuku.alkitab.base.audio

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
 * Resolves which audio recordings ([AudioSet]s) exist for one Bible version, via
 * the backend's `GET /audio/sets/<preset>`. Every public method is safe to call
 * from any thread.
 *
 * A version with no preset name short-circuits to an empty set list without a
 * request. Answers are cached in memory for the life of the process, negative
 * ones included, so a version with no audio does not re-query on every chapter
 * turn. A failure also resolves to an empty list, so the entry point is hidden
 * instead of showing a button that does nothing. That answer expires after
 * [FAILURE_RETRY_AFTER_NANOS], so a single failure at startup does not hide
 * audio for the rest of the process.
 */
object AudioSetsRepository {

    private const val TAG = "AudioSetsRepo"
    private const val SETS_PATH = "/audio/sets/"

    /** Long enough that an outage cannot become a request per chapter turn. */
    private val FAILURE_RETRY_AFTER_NANOS = TimeUnit.SECONDS.toNanos(30)

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

    /** Clock seam so the failure cooldown is testable without a real wait. */
    internal var nanoTime: () -> Long = System::nanoTime

    /** [staleAtNanos] is null for a resolved answer, a deadline for a failed one. */
    private class Entry(val sets: AudioSets, val staleAtNanos: Long?)

    private val cache = ConcurrentHashMap<String, Entry>()

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

    /** Does not throw; a failure resolves to an empty set list. */
    suspend fun setsFor(versionId: String): AudioSets {
        liveEntry(versionId)?.let { return it.sets }
        val deferred = mutex.withLock {
            liveEntry(versionId)?.let { return it.sets }
            inFlight.getOrPut(versionId) {
                scope.async {
                    val entry = resolveEntry(versionId)
                    cache[versionId] = entry
                    mutex.withLock { inFlight.remove(versionId) }
                    entry.sets
                }
            }
        }
        return deferred.await()
    }

    /**
     * Non-blocking peek at the in-memory cache. Null means [versionId] has not
     * been resolved yet, or its last resolution failed and the cooldown has
     * passed. Callers (menu preparation) hide the entry point and kick off
     * [setsFor], re-preparing once it lands.
     */
    fun cachedSetsFor(versionId: String): AudioSets? = liveEntry(versionId)?.sets

    /**
     * Drops the cached answer for [versionId] so the next [setsFor] queries
     * again. The audio bar's user-initiated retry uses this to re-test an
     * answer without waiting out the failure cooldown.
     */
    fun invalidate(versionId: String) {
        cache.remove(versionId)
    }

    /** The cached entry for [versionId], dropping and reporting an expired one as absent. */
    private fun liveEntry(versionId: String): Entry? {
        val entry = cache[versionId] ?: return null
        val staleAt = entry.staleAtNanos ?: return entry
        // Compare by subtraction so the check survives nanoTime()'s wraparound.
        if (nanoTime() - staleAt < 0) return entry
        cache.remove(versionId, entry)
        return null
    }

    /** Only a transport or parse failure gets a deadline; everything else is an answer. */
    private suspend fun resolveEntry(versionId: String): Entry {
        val presetName = presetNameResolver.presetNameFor(versionId)
            ?: return Entry(emptySets(""), null)
        val sets = fetchSets(presetName)
            ?: return Entry(emptySets(presetName), nanoTime() + FAILURE_RETRY_AFTER_NANOS)
        return Entry(sets, null)
    }

    /** Null on any transport or parse failure, so the caller can tell it apart from an empty answer. */
    private suspend fun fetchSets(presetName: String): AudioSets? {
        val url = BuildConfig.SERVER_HOST + SETS_PATH + presetName
        val body = http.getBody(url) ?: return null
        parseSets(body)?.let { return it }
        // The unparseable bytes may be a corrupted entry served from the HTTP
        // disk cache. Fetch once past the cache, which also replaces the
        // entry, and give the fresh payload a parse.
        AppLog.w(TAG, "audio sets for $presetName did not parse; refetching past the HTTP cache")
        val fresh = http.getBodyRevalidating(url) ?: return null
        return parseSets(fresh)
    }

    private fun parseSets(body: String): AudioSets? {
        return try {
            json.decodeFromString(AudioSets.serializer(), body)
        } catch (e: SerializationException) {
            AppLog.w(TAG, "audio sets JSON did not parse: ${e.message}")
            null
        }
    }

    /** Synthesized "no audio" answer; `schema = 0` marks it as client-made. */
    private fun emptySets(preset: String) = AudioSets(schema = 0, preset = preset, sets = emptyList())

    /**
     * [VersionManager.getVersionFromVersionId] returns null for the internal
     * version by contract, so that id is mapped to the internal [MVersion]
     * first. The preset read itself has no per-subtype branching.
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

    /** Test-only. Assumes there are no concurrent [setsFor] callers. */
    internal fun resetForTest() {
        cache.clear()
        inFlight.clear()
        presetNameResolver = defaultPresetNameResolver
        http = defaultHttp
        nanoTime = System::nanoTime
    }
}
