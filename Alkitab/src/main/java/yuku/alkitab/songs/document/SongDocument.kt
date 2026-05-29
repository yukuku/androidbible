package yuku.alkitab.songs.document

import kotlinx.serialization.Serializable

/**
 * Top-level canonical JSON document model for a song.
 *
 * Represented as an ordered list of layout [Block]s that the author controls,
 * not a fixed set of fields. Used everywhere: on-disk storage, download wire format,
 * and the output of the txt generator.
 *
 * @param v Schema version. Defaults to 1.
 * @param code Required lookup key (per-book identity).
 * @param meta Derived metadata ([SongMeta]) denormalized for index/search.
 *             Never hand-authored; derived from [Block]s with role "title" / "title_original".
 * @param blocks Ordered list of layout blocks.
 */
@Serializable
data class SongDocument(
    val v: Int = 1,
    val code: String,
    val meta: SongMeta?,
    @Serializable(with = BlockListSerializer::class)
    val blocks: List<Block>
)

/**
 * Derived metadata for a [SongDocument], denormalized for index/search.
 *
 * @param title Text of the first block with role "title".
 * @param title_original Text of the first block with role "title_original" (null if none).
 */
@Serializable
data class SongMeta(
    val title: String?,
    val title_original: String?
)