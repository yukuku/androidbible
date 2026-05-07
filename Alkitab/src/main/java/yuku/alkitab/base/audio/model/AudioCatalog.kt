package yuku.alkitab.base.audio.model

import kotlinx.serialization.Serializable

/**
 * Top-level catalog returned by the backend's `GET /audio/catalog`, also the shape
 * stored in `files/audio_catalog.json` (overrides) and `assets/audio_catalog.json`
 * (bundled fallback).
 */
@Serializable
data class AudioCatalog(
    val generatedAt: Long = 0L,
    val entries: List<AudioVersion> = emptyList(),
)
