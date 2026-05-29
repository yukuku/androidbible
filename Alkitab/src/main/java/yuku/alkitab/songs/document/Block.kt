package yuku.alkitab.songs.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Sealed class hierarchy for all block types in a [SongDocument].
 *
 * Discriminated by [type]. Every block may carry an optional [size] (float;
 * 1 = normal relative font size). [PBlock] additionally accepts an optional
 * line-level [PBlock.align].
 *
 * Unknown [type] or unknown [role] renders as a plain paragraph and is
 * otherwise ignored — the format is forward-compatible.
 */
@Suppress("unused")
@Serializable(with = BlockSerializer::class)
sealed class Block {
    /** Block type discriminator: "p", "row", "lyric", "scripture", or "youtube". */
    abstract val type: String

    /** Optional role for default formatting. See design.md §3.5 for known roles. */
    abstract val role: String?

    /** Optional relative font size (1 = normal). */
    abstract val size: Float?
}

/**
 * A single line of text.
 *
 * @param role Optional role for default formatting.
 * @param size Optional relative font size override.
 * @param align Optional line-level alignment: "start", "center", or "end"
 *              (writing-direction relative, not left/right).
 * @param content The line content as a [Line] (list of [Span]s).
 */
@Serializable
@SerialName("p")
data class PBlock(
    override val role: String? = null,
    override val size: Float? = null,
    val align: String? = null,
    @Serializable(with = LineSerializer::class)
    val content: Line
) : Block() {
    override val type = "p"
}

/**
 * A horizontal container for [PBlock] items spread start → end.
 * Used for "lyricist left / composer right" rows.
 *
 * @param role Optional role for default formatting.
 * @param size Optional relative font size override.
 * @param items List of [PBlock] items; first = start/left, last = end/right.
 */
@Serializable
@SerialName("row")
data class RowBlock(
    override val role: String? = null,
    override val size: Float? = null,
    val items: List<PBlock>
) : Block() {
    override val type = "row"
}

/**
 * A lyric group (stanza set).
 *
 * @param role Optional role for default formatting.
 * @param size Optional relative font size override.
 * @param caption Optional [Line] label for the group.
 * @param verses Ordered list of [Verse]s in this group.
 */
@Serializable
@SerialName("lyric")
data class LyricBlock(
    override val role: String? = null,
    override val size: Float? = null,
    @Serializable(with = LineSerializer::class)
    val caption: Line? = null,
    val verses: List<Verse>
) : Block() {
    override val type = "lyric"
}

/**
 * A scripture reference block.
 *
 * @param role Optional role for default formatting.
 * @param size Optional relative font size override.
 * @param osis OSIS string, e.g. "John.3.16; Rom.5.8".
 */
@Serializable
@SerialName("scripture")
data class ScriptureBlock(
    override val role: String? = null,
    override val size: Float? = null,
    val osis: String
) : Block() {
    override val type = "scripture"
}

/**
 * An embedded YouTube reference.
 *
 * @param role Optional role for default formatting.
 * @param size Optional relative font size override.
 * @param videoId The YouTube video ID (required).
 */
@Serializable
@SerialName("youtube")
data class YoutubeBlock(
    override val role: String? = null,
    override val size: Float? = null,
    val videoId: String
) : Block() {
    override val type = "youtube"
}

/**
 * Fallback block for unknown block types encountered during deserialization.
 * Preserves the raw JSON for round-tripping.
 *
 * @param type The original type string from the JSON.
 * @param rawJson The raw JSON object of the unknown block.
 */
@Serializable(with = UnknownBlockSerializer::class)
data class UnknownBlock(
    override val type: String,
    val rawJson: JsonObject
) : Block() {
    override val role = null
    override val size = null
}