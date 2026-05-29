package yuku.alkitab.songs.document

import kotlinx.serialization.Serializable

/**
 * A verse within a [LyricBlock].
 *
 * [kind] maps from the legacy [VerseKind] (NORMAL / REFRAIN / TEXT):
 * - [VerseKind.NORMAL] → numbered (positional) and indented.
 * - [VerseKind.REFRAIN] → "Ref.:" label, no number, italic/indented.
 * - [VerseKind.TEXT] → spoken/instruction; no number, plain.
 *
 * [marker] is an optional explicit label that overrides the positional number.
 *
 * @param kind The verse kind controlling display formatting.
 * @param marker Optional explicit label overriding positional numbering.
 * @param lines Ordered list of [VerseLine]s (the lyric text content).
 */
@Serializable
data class Verse(
    val kind: VerseKind,
    val marker: String? = null,
    val lines: List<VerseLine>
)

/**
 * A single line within a [Verse], optionally carrying line-level formatting.
 *
 * [size] and [align] apply only to this line (overriding block defaults).
 * [content] is the line text as a [Line].
 *
 * @param size Optional relative font size for this line only.
 * @param align Optional alignment for this line ("start", "center", "end").
 * @param content The line text as a [Line].
 */
@Serializable(with = VerseLineSerializer::class)
data class VerseLine(
    val size: Float? = null,
    val align: String? = null,
    @Serializable(with = LineSerializer::class)
    val content: Line
)