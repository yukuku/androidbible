package yuku.alkitab.songs.document

import kotlinx.serialization.Serializable

/**
 * A line of text — either a plain string or a list of styled runs.
 *
 * In JSON: a plain line is emitted as a plain string; a line containing
 * styled runs is a [Span][].
 *
 * A [Span] carries text plus optional inline styles ("u" = underline,
 * "b" = bold, "i" = italic).
 */
typealias Line = List<Span>

/**
 * A styled run of text within a [Line].
 *
 * @param text The actual text content.
 * @param style Optional list of style labels: "u" (underline),
 *              "b" (bold), "i" (italic).
 */
@Serializable
data class Span(
    val text: String,
    val style: List<String>? = null
)

/**
 * Constructs a plain [Line] (no styling) from a single [text] string.
 */
fun plainLine(text: String): Line = listOf(Span(text))