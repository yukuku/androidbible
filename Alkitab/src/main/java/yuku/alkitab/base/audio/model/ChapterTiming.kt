package yuku.alkitab.base.audio.model

import kotlinx.serialization.Serializable

/**
 * Verse timing for a single chapter of one recording. Mirrors the JSON
 * returned by the backend's `GET /audio/timing/<preset>/<audioId>/<book_1>/<chapter_1>.json`
 * (see docs/features/audio-bible/backend-contract.md).
 *
 * Timings are integer milliseconds, already normalized and sorted by
 * `startMs`; the app does no unit conversion. `durationMs` is informational:
 * `ExoPlayer.duration` after preparing the MP3 is the source of truth for the
 * scrubber max. [verses] may be empty when the chapter has audio but no
 * upstream timing; the client plays without highlight in that case.
 *
 * Every field is required. See [AudioSets] for why the models declare no
 * default parameter values.
 */
@Serializable
data class ChapterTiming(
    val schema: Int,
    val preset: String,
    val audioId: String,
    val book_1: Int,
    val chapter_1: Int,
    val durationMs: Long,
    val verses: List<VerseTiming>,
)
