package yuku.alkitab.base.audio.model

import kotlinx.serialization.Serializable

/**
 * Verse timing for a single chapter. Mirrors the JSON returned by the backend's
 * `GET /audio/timing` endpoint (see docs/features/audio-bible/backend-plan.md §4.2).
 *
 * `durationMs` is informational — `ExoPlayer.duration` after preparing the MP3 is
 * the source of truth for the scrubber max. `verses` may be empty when the chapter
 * has audio but no upstream timing; the client renders audio without highlight in
 * that case.
 */
@Serializable
data class ChapterTiming(
    val schema: Int = 1,
    val versionId: String,
    val bookId: Int,
    val chapter_1: Int,
    val durationMs: Long = 0L,
    val verses: List<VerseTiming> = emptyList(),
    val generatedAt: Long = 0L,
)
