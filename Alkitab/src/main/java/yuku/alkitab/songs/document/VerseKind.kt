package yuku.alkitab.songs.document

import kotlinx.serialization.SerialName

/**
 * Verse kind enum — maps to the JSON string "normal", "refrain", or "text".
 *
 * - [NORMAL] — numbered (positional within the group) and indented.
 * - [REFRAIN] — "Ref.:" label, no number, italic/indented.
 * - [TEXT]   — spoken/instruction; no number, plain.
 */
enum class VerseKind {
    @SerialName("normal") NORMAL,
    @SerialName("refrain") REFRAIN,
    @SerialName("text") TEXT
}