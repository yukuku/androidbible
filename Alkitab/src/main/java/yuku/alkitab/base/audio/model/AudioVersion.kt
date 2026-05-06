package yuku.alkitab.base.audio.model

import kotlinx.serialization.Serializable

/**
 * One entry in the audio catalog: a version that has audio available, plus the
 * URL templates the client uses to fetch chapter MP3s and timing JSON. Both
 * templates are relative paths on the backend (`BuildConfig.SERVER_HOST`); the
 * client expands `{bookId}` and `{chapter_1}` via literal string replace.
 *
 * `versionId` matches the client's [yuku.alkitab.base.model.MVersion.getVersionId]
 * format — typically `"preset/<preset_name>"` (e.g. `preset/in-tb`).
 */
@Serializable
data class AudioVersion(
    val versionId: String,
    val shortName: String,
    val displayLocaleHint: String? = null,
    val chapterUrlTemplate: String,
    val timingUrlTemplate: String? = null,
)
