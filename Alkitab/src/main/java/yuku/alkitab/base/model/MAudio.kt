package yuku.alkitab.base.model

/**
 * Describes an audio source for a given Bible version.
 *
 * @param versionId the [MVersion] Bible version identifier (e.g. "preset/in-tb")
 * @param audioFolder the folder name on the audio server (e.g. "tb_alkitabsuara")
 * @param timingVersionParam the `version` query parameter for the timing API; defaults to [versionId]
 * @param supportsDual whether this version supports dual-audio (split-view interleaved) mode
 */
data class MAudio(
    val versionId: String,
    val audioFolder: String,
    val timingVersionParam: String,
    val supportsDual: Boolean = false,
)
