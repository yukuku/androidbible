package yuku.alkitab.base.audio.model

import kotlinx.serialization.Serializable

/**
 * Response of the backend's `GET /audio/sets/<preset>`: every recording that
 * exists for one Bible version. See docs/features/audio-bible/backend-contract.md.
 *
 * [sets] is ordered — `sets[0]` is the default recording for the version. An
 * empty [sets] means "no audio for this version", which is a normal answer,
 * not an error.
 *
 * Every field is required on purpose: the backend always emits all of them
 * (`sets` is `[]` rather than absent when a version has no audio), so a
 * missing field signals a contract change and must fail loudly at parse time
 * instead of yielding a silently-empty model.
 */
@Serializable
data class AudioSets(
    val schema: Int,
    val preset: String,
    val sets: List<AudioSet>,
)
