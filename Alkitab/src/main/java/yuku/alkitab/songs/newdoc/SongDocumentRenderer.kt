package yuku.alkitab.songs.newdoc

/**
 * Walks the [LyricBlock]s of a [SongDocument] and produces the same
 * `lyric` / `verse` / `verse_content` / `line` HTML shape as the legacy
 * `SongFragment.songToHtml`, so it drops into the existing
 * `templates/song.html` unchanged (design §9). Other block types (title,
 * tune, authors, scripture, musical) are rendered by the caller into the
 * template's own `{{div:...}}` placeholders — see [SongFragment].
 */
object SongDocumentRenderer {
    @JvmStatic
    @JvmOverloads
    fun render(doc: SongDocument, forPatchText: Boolean = false): String {
        val lyricBlocks = doc.blocks.filterIsInstance<LyricBlock>()
        val sb = StringBuilder()

        for ((i, lyricBlock) in lyricBlocks.withIndex()) {
            sb.append("<div class='lyric'>")
            if (lyricBlocks.size > 1 || lyricBlock.caption != null) {
                if (lyricBlock.caption != null) {
                    sb.append("<div class='lyric_caption'>").append(renderLineHtml(lyricBlock.caption)).append("</div>")
                } else {
                    sb.append("<div class='lyric_caption'>Versi ").append(i + 1).append("</div>")
                }
            }

            var verseNumberNormal = 0
            var verseNumberReff = 0
            for (verse in lyricBlock.verses) {
                sb.append("<div class='verse").append(if (verse.kind == VerseKind.REFRAIN) " refrain" else "").append("'>")

                when (verse.kind) {
                    VerseKind.REFRAIN -> verseNumberReff++
                    VerseKind.NORMAL -> verseNumberNormal++
                    VerseKind.TEXT -> {}
                }

                if (forPatchText) {
                    when (verse.kind) {
                        VerseKind.REFRAIN -> sb.append("reff ").append(verseNumberReff)
                        VerseKind.NORMAL -> sb.append(verseNumberNormal)
                        VerseKind.TEXT -> {}
                    }
                } else {
                    when (verse.kind) {
                        VerseKind.REFRAIN -> sb.append("<div class='verse_ordering'>").append(verseNumberReff).append("</div>")
                        VerseKind.NORMAL -> sb.append("<div class='verse_ordering'>").append(verseNumberNormal).append("</div>")
                        VerseKind.TEXT -> {}
                    }
                }

                sb.append("<div class='verse_content'>")
                for (line in verse.lines) {
                    val html = renderVerseLineHtml(line)
                    if (forPatchText) {
                        sb.append(html).append("<br/>")
                    } else {
                        sb.append("<p class='line'>").append(html).append("</p>")
                    }
                }
                sb.append("</div>")
                sb.append("</div>")
            }
            sb.append("</div>")
        }

        return sb.toString()
    }

    private fun renderVerseLineHtml(vl: VerseLine): String = when (vl) {
        is VerseLine.Simple -> renderLineHtml(vl.line)
        is VerseLine.Wrapped -> {
            val style = buildString {
                vl.size?.let { append("font-size:").append(it).append("em;") }
                vl.align?.let { append("text-align:").append(it).append(";") }
            }
            val inner = renderLineHtml(vl.content)
            if (style.isEmpty()) inner else "<span style='$style'>$inner</span>"
        }
    }

    @JvmStatic
    fun renderLineHtml(line: Line): String = when (line) {
        is Line.Plain -> escapeHtml(line.text)
        is Line.Styled -> line.spans.joinToString("") { span -> wrapStyle(escapeHtml(span.text), span.style) }
    }

    private fun wrapStyle(text: String, styles: List<String>): String {
        var result = text
        for (style in styles.asReversed()) {
            result = "<$style>$result</$style>"
        }
        return result
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
