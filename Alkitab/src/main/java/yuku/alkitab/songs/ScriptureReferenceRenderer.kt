package yuku.alkitab.songs

import yuku.alkitab.base.App
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.OsisBookNames

private const val TAG = "ScriptureReferenceRenderer"

/**
 * Converts scripture ref lines like `B1.C1.V1-B2.C2.V2; B3.C3.V3` into localized, readable text,
 * optionally linked: `<a href='protocol:B1.C1.V1-B2.C2.V2'>Book 1 c1:v1-v2</a>; ...`. Localizes
 * book names against the active Bible version. Shared by [SongViewActivity] (copy/share plain
 * text) and [SongFragment] (in-document HTML rendering of a `ScriptureBlock`).
 */
object ScriptureReferenceRenderer {
    /**
     * One resolved scripture reference: the human-[readable] text and the
     * [osisId] (or `osisId0-osisId1` range) it links to. Emitted only for
     * references that resolved against the active version — unresolvable
     * ranges are dropped, exactly as [render] drops them from its output.
     */
    data class Part(val readable: String, val osisId: String)

    /**
     * @param protocol null to output plain text (no links); non-null to wrap each reference in an
     * `<a href='protocol:osisId'>` link.
     * @param line scripture ref(s) in OSIS
     */
    @JvmStatic
    fun render(protocol: String?, line: String?): String {
        val sb = StringBuilder()
        for (part in renderParts(line)) {
            if (sb.isNotEmpty()) sb.append("; ")
            appendScriptureReferenceLink(sb, protocol, part.osisId, part.readable)
        }
        return sb.toString()
    }

    /**
     * Framework-free counterpart of [render]: resolves each `;`-separated
     * reference in [line] to a [Part]. Shared by the WebView HTML path (via
     * [render]) and the Compose renderer, which builds its own clickable
     * spans from these parts. Preserves [render]'s drop-on-failure semantics.
     */
    @JvmStatic
    fun renderParts(line: String?): List<Part> {
        if (line.isNullOrBlank()) return emptyList()

        val parts = mutableListOf<Part>()

        val ranges = line.split("\\s*;\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (range in ranges) {
            val osisIds = if (range.indexOf('-') >= 0) {
                range.split("\\s*-\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            } else {
                arrayOf(range)
            }

            if (osisIds.size == 1) {
                val osisId = osisIds[0]
                val readable = osisIdToReadable(line, osisId, null, null)
                if (readable != null) {
                    parts.add(Part(readable, osisId))
                }
            } else if (osisIds.size == 2) {
                val bcv = intArrayOf(-1, 0, 0)

                val osisId0 = osisIds[0]
                val readable0 = osisIdToReadable(line, osisId0, null, bcv)
                val osisId1 = osisIds[1]
                val readable1 = osisIdToReadable(line, osisId1, bcv, null)
                if (readable0 != null && readable1 != null) {
                    parts.add(Part("$readable0-$readable1", "$osisId0-$osisId1"))
                }
            }
        }

        return parts
    }

    private fun appendScriptureReferenceLink(sb: StringBuilder, protocol: String?, osisId: String, readable: String) {
        if (protocol != null) {
            sb.append("<a href='")
            sb.append(protocol)
            sb.append(':')
            sb.append(osisId)
            sb.append("'>")
        }
        sb.append(readable)
        if (protocol != null) {
            sb.append("</a>")
        }
    }

    /**
     * @param compareWithRangeStart if this is the second part of a range, set this to non-null, with [0] is bookId and [1] chapter_1.
     * @param outBcv if not null and length is >= 3, will be filled with parsed bcv
     */
    private fun osisIdToReadable(line: String, osisId: String, compareWithRangeStart: IntArray?, outBcv: IntArray?): String? {
        var res: String? = null

        val parts = osisId.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size != 2 && parts.size != 3) {
            AppLog.w(TAG, "osisId invalid: $osisId in $line")
        } else {
            val bookName = parts[0]
            val chapter_1 = Integer.parseInt(parts[1])
            val verse_1 = if (parts.size < 3) 0 else Integer.parseInt(parts[2])

            val bookId = OsisBookNames.osisBookNameToBookId(bookName)

            if (outBcv != null && outBcv.size >= 3) {
                outBcv[0] = bookId
                outBcv[1] = chapter_1
                outBcv[2] = verse_1
            }

            if (bookId < 0) {
                AppLog.w(TAG, "osisBookName invalid: $bookName in $line")
            } else {
                val book = App.services.versions.activeVersion().getBook(bookId)

                if (book != null) {
                    var full = true
                    if (compareWithRangeStart != null) {
                        if (compareWithRangeStart[0] == bookId) {
                            if (compareWithRangeStart[1] == chapter_1) {
                                res = verse_1.toString()
                                full = false
                            } else {
                                res = "$chapter_1:$verse_1"
                                full = false
                            }
                        }
                    }

                    if (full) {
                        res = if (verse_1 == 0) book.reference(chapter_1) else book.reference(chapter_1, verse_1)
                    }
                }
            }
        }
        return res
    }
}
