package yuku.alkitab.base.audio

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import yuku.afw.storage.Preferences
import yuku.alkitab.base.audio.model.AudioSet
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog

/**
 * Which recording plays for each version — a per-version user choice persisted
 * as a JSON object in [Prefkey.audioSelectedSets], e.g.
 * `{"preset/in-tb": "davar"}`. One preference key holds the whole map:
 * [Prefkey] is an enum, so per-version keys are not expressible, and the map
 * stays small (one entry per version the user has actually played).
 *
 * Selection rules ([resolve]):
 * - **Default** is `sets[0]`, i.e. backend order — editorial control over
 *   which recording a new user hears lives server-side.
 * - A **remembered** audioId is honored when it is still in the set list.
 * - A remembered audioId **absent from a later response** (upstream dropped
 *   it, or an admin disabled it) falls back to `sets[0]` and overwrites the
 *   stored choice, so a stale preference never leaves the user with a dead
 *   entry point.
 *
 * A remembered set that merely doesn't cover the current book is NOT switched
 * here — coverage gating is the caller's concern, and an unannounced narrator
 * change mid-book is worse than a temporarily absent button.
 */
object AudioSetSelections {

    private const val TAG = "AudioSetSelections"

    private val json = Json
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    /**
     * Storage seam over [Preferences] so the selection logic stays testable in
     * plain JUnit without the Android SharedPreferences machinery.
     */
    internal var storedJsonReader: () -> String? = { Preferences.getString(Prefkey.audioSelectedSets) }
    internal var storedJsonWriter: (String) -> Unit = { Preferences.setString(Prefkey.audioSelectedSets, it) }

    /**
     * Resolves the selected recording for [versionId] against [sets], applying
     * the default and fallback rules above. Null iff [sets] is empty.
     */
    fun resolve(versionId: String, sets: List<AudioSet>): AudioSet? {
        val remembered = parseMap(storedJsonReader())[versionId]
        val chosen = choose(sets, remembered) ?: return null
        if (remembered != null && chosen.audioId != remembered) {
            store(versionId, chosen.audioId)
        }
        return chosen
    }

    /** Persists [audioId] as the selected recording for [versionId]. */
    fun store(versionId: String, audioId: String) {
        val map = parseMap(storedJsonReader()) + (versionId to audioId)
        storedJsonWriter(encodeMap(map))
    }

    /** The remembered set when still available, otherwise the default `sets[0]`. */
    internal fun choose(sets: List<AudioSet>, remembered: String?): AudioSet? =
        sets.firstOrNull { it.audioId == remembered } ?: sets.firstOrNull()

    internal fun parseMap(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            json.decodeFromString(mapSerializer, raw)
        } catch (e: SerializationException) {
            AppLog.w(TAG, "stored selection map did not parse: ${e.message}")
            emptyMap()
        }
    }

    internal fun encodeMap(map: Map<String, String>): String =
        json.encodeToString(mapSerializer, map)
}
