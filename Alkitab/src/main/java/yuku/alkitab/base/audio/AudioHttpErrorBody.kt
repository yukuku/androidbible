package yuku.alkitab.base.audio

/**
 * Formats an error response's body for the audio load log. Backends often
 * explain a 4xx/5xx in the body, and that explanation is exactly what the log
 * sheet exists to surface.
 *
 * Only bodies smaller than [MAX_ERROR_BODY_BYTES] are shown, because a large
 * body is far more likely to be an HTML error page than a useful message, and
 * the log is capped memory. Returns null when there is nothing worth showing.
 *
 * A body that is entirely printable ASCII (plus the usual whitespace) is shown
 * as text with newlines and tabs folded to spaces, so one entry stays one line.
 * Anything containing a byte outside 32..126 is treated as binary and rendered
 * as a hex dump of the first [HEX_DUMP_BYTES] bytes followed by `...`.
 */
internal const val MAX_ERROR_BODY_BYTES = 1024
private const val HEX_DUMP_BYTES = 16

internal fun formatErrorBody(body: ByteArray): String? {
    if (body.isEmpty() || body.size >= MAX_ERROR_BODY_BYTES) return null
    return if (body.all { isPrintableOrWhitespace(it) }) {
        val text = String(body, Charsets.US_ASCII)
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .trim()
        text.ifEmpty { null }
    } else {
        body.take(HEX_DUMP_BYTES).joinToString(" ") { "%02x".format(it) } + " ..."
    }
}

/**
 * Printable ASCII (32..126), plus the whitespace bytes that get folded to
 * spaces above. A body that is plain text apart from its line endings should
 * still be shown as text, not hex-dumped.
 */
private fun isPrintableOrWhitespace(b: Byte): Boolean {
    val v = b.toInt() and 0xff
    return v in 32..126 || v == '\n'.code || v == '\r'.code || v == '\t'.code
}
