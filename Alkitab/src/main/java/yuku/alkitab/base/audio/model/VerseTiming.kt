package yuku.alkitab.base.audio.model

import kotlinx.serialization.Serializable

@Serializable
data class VerseTiming(
    val verse_1: Int,
    val startMs: Long,
    val endMs: Long,
)
