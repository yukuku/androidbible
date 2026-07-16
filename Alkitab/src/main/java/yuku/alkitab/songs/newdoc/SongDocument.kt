package yuku.alkitab.songs.newdoc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Canonical portable-song document model. A song is an ordered list of
 * layout [blocks], not a fixed set of fields. See
 * `docs/features/portable-songs/design.md` for the full JSON schema this
 * mirrors.
 */
@Serializable
data class SongDocument(
    val code: String,
    val meta: Meta = Meta(),
    val blocks: List<Block> = emptyList(),
)

@Serializable
data class Meta(
    val title: String? = null,
    val title_original: String? = null,
)

@Serializable
enum class VerseKind {
    @SerialName("normal") NORMAL,
    @SerialName("refrain") REFRAIN,
    @SerialName("text") TEXT,
}

@Serializable
data class Verse(
    val kind: VerseKind,
    val marker: String? = null,
    val lines: List<VerseLine> = emptyList(),
)

@Serializable
data class Span(
    val text: String,
    val style: List<String> = emptyList(),
)

/**
 * `Line = string | Span[]`. A line with no inline styling is a plain JSON
 * string; a line with styled runs is a `Span[]`. See [LineSerializer] for
 * the JSON-shape branching.
 */
@Serializable(with = LineSerializer::class)
sealed interface Line {
    data class Plain(val text: String) : Line
    data class Styled(val spans: List<Span>) : Line

    companion object {
        fun of(text: String): Line = Plain(text)
    }
}

/**
 * `VerseLine = Line | { size?, align?, content: Line }`.
 */
@Serializable(with = VerseLineSerializer::class)
sealed interface VerseLine {
    data class Simple(val line: Line) : VerseLine
    data class Wrapped(val size: Float? = null, val align: String? = null, val content: Line) : VerseLine
}

/**
 * A block is discriminated by `type`. Every block carries an optional
 * [size]. Unknown types decode into [UnknownBlock] instead of failing,
 * keeping the format forward-compatible.
 */
@Serializable(with = BlockSerializer::class)
sealed interface Block {
    val size: Float?
}

@Serializable(with = PBlockSerializer::class)
data class PBlock(
    val role: String? = null,
    override val size: Float? = null,
    val align: String? = null,
    val content: Line,
) : Block

@Serializable(with = RowBlockSerializer::class)
data class RowBlock(
    override val size: Float? = null,
    val items: List<Block> = emptyList(),
) : Block

@Serializable(with = LyricBlockSerializer::class)
data class LyricBlock(
    val role: String? = null,
    override val size: Float? = null,
    val caption: Line? = null,
    val verses: List<Verse> = emptyList(),
) : Block

@Serializable(with = ScriptureBlockSerializer::class)
data class ScriptureBlock(
    val role: String? = null,
    override val size: Float? = null,
    val osis: String,
) : Block

@Serializable(with = YoutubeBlockSerializer::class)
data class YoutubeBlock(
    val role: String? = null,
    override val size: Float? = null,
    val videoId: String,
) : Block

@Serializable(with = GapBlockSerializer::class)
data class GapBlock(
    override val size: Float? = null,
) : Block

/**
 * Forward-compatibility sink: an unknown `type` renders as a plain
 * paragraph and round-trips its original JSON object unchanged.
 */
data class UnknownBlock(
    val type: String,
    val raw: JsonObject,
) : Block {
    override val size: Float? = null
}

fun Line.plainText(): String = when (this) {
    is Line.Plain -> text
    is Line.Styled -> spans.joinToString("") { it.text }
}

fun VerseLine.plainText(): String = when (this) {
    is VerseLine.Simple -> line.plainText()
    is VerseLine.Wrapped -> content.plainText()
}
