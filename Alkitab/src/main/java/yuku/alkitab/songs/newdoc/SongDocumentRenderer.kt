package yuku.alkitab.songs.newdoc

import yuku.alkitab.songs.newdoc.SongDocumentRenderer.renderLyrics


/**
 * Renders a [SongDocument] to the HTML shape `templates/song.html` expects,
 * by walking [SongDocument.blocks] in document order and dispatching per
 * block type. [renderLyrics] renders only the [LyricBlock]s, for callers
 * (the patch-text bridge test) that need just the verse HTML.
 */
object SongDocumentRenderer {
    // role is an open vocabulary (design §3.5): anything outside this set falls back to "body"
    // instead of being used verbatim as a CSS class name, since role text could otherwise carry
    // attacker-controlled content into a class='...' attribute.
    private val KNOWN_ROLES = setOf(
        "title", "title_original", "tune", "musical",
        "authors_lyric", "authors_music", "note", "copyright",
    )

    private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    @JvmStatic
    fun renderDocument(doc: SongDocument, renderScripture: (String) -> String, forPatchText: Boolean): String {
        val sb = StringBuilder()
        val lyricBlocks = doc.blocks.filterIsInstance<LyricBlock>()
        var lyricBlockIndex = 0

        for (block in doc.blocks) {
            when (block) {
                is PBlock -> {
                    renderPBlock(block, sb)
                    if (!forPatchText) sb.append("<div class='break'></div>")
                }

                is RowBlock -> {
                    renderRowBlock(block, sb, renderScripture)
                    if (!forPatchText) sb.append("<div class='break'></div>")
                }

                is LyricBlock -> {
                    renderLyricBlock(block, lyricBlockIndex, lyricBlocks.size, sb, forPatchText)
                    lyricBlockIndex++
                }

                is ScriptureBlock -> sb.append("<div class='scriptureReferences'>").append(renderScripture(block.osis)).append("</div>")
                is YoutubeBlock -> renderYoutubeBlock(block, sb)
                is GapBlock -> sb.append("<div style='height:").append(block.size ?: 1f).append("em'></div>")
                is UnknownBlock -> {} // forward-compat sink: unrecognized block types are silently skipped
            }
        }

        return sb.toString()
    }

    private fun renderRowBlock(block: RowBlock, sb: StringBuilder, renderScripture: (String) -> String) {
        val cls = "row"
        val style = buildString {
            block.size?.takeIf { it.isFinite() }?.let { append("font-size:").append(it).append("em;") }
        }
        sb.append("<div class='").append(cls).append("'")
        if (style.isNotEmpty()) sb.append(" style='").append(style).append("'")
        sb.append(">")

        for (item in block.items) {
            when (item) {
                is PBlock -> renderPBlock(item, sb, inRow = true)
                is YoutubeBlock -> renderYoutubeBlock(item, sb)
                is ScriptureBlock -> sb.append("<div class='scriptureReferences'>").append(renderScripture(item.osis)).append("</div>")
                is GapBlock -> sb.append("<div style='height:").append(item.size ?: 1f).append("em'></div>")
                else -> {}
            }
        }

        sb.append("</div>")
    }

    @JvmStatic
    fun renderLyrics(doc: SongDocument, forPatchText: Boolean): String {
        val lyricBlocks = doc.blocks.filterIsInstance<LyricBlock>()
        val sb = StringBuilder()
        for ((i, lyricBlock) in lyricBlocks.withIndex()) {
            renderLyricBlock(lyricBlock, i, lyricBlocks.size, sb, forPatchText)
        }
        return sb.toString()
    }

    private fun renderPBlock(block: PBlock, sb: StringBuilder, inRow: Boolean = false) {
        val cls = block.role?.takeIf { it in KNOWN_ROLES } ?: "body"
        val style = buildString {
            block.size?.takeIf { it.isFinite() }?.let { append("font-size:").append(it).append("em;") }
            block.align?.takeIf { it in ALLOWED_ALIGNS }?.let { align ->
                if (inRow) {
                    // A row item is a shrink-to-fit flex item, so text-align cannot move
                    // it; align positions the item itself along the row's main axis.
                    when (align) {
                        "end" -> append("margin-inline-start:auto;")
                        "center" -> append("margin-inline-start:auto;margin-inline-end:auto;")
                        else -> {} // start: flex default
                    }
                } else {
                    append("text-align:").append(align).append(";")
                }
            }
        }
        sb.append("<div class='").append(cls).append("'")
        if (style.isNotEmpty()) sb.append(" style='").append(style).append("'")
        sb.append(">").append(renderLineHtml(block.content)).append("</div>")
    }

    private fun renderYoutubeBlock(block: YoutubeBlock, sb: StringBuilder) {
        val videoId = block.videoId
        if (!YOUTUBE_ID_REGEX.matches(videoId)) return // not a valid id; skip rather than build an unsafe URL

        sb.append("<div class='youtube'><a href='youtube:")
            .append(videoId)
            .append("'>YouTube</a></div>")
    }

    private fun renderLyricBlock(lyricBlock: LyricBlock, index: Int, totalCount: Int, sb: StringBuilder, forPatchText: Boolean) {
        sb.append("<div class='lyric'>")
        if (totalCount > 1 || lyricBlock.caption != null) {
            if (lyricBlock.caption != null) {
                sb.append("<div class='lyric_caption'>").append(renderLineHtml(lyricBlock.caption)).append("</div>")
            } else {
                sb.append("<div class='lyric_caption'>Versi ").append(index + 1).append("</div>")
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

    // align is only ever start/center/end. Same rationale as ALLOWED_STYLES below — this value
    // lands inside a single-quoted style='' attribute, so an unvalidated string could break out of it.
    private val ALLOWED_ALIGNS = setOf("start", "center", "end")

    private fun renderVerseLineHtml(vl: VerseLine): String = when (vl) {
        is VerseLine.Simple -> renderLineHtml(vl.line)
        is VerseLine.Wrapped -> {
            val style = buildString {
                vl.size?.takeIf { it.isFinite() }?.let { append("font-size:").append(it).append("em;") }
                vl.align?.takeIf { it in ALLOWED_ALIGNS }?.let { append("text-align:").append(it).append(";") }
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

    // Span.style is only ever u/b/i. The WebView has JavaScript enabled, so a maliciously crafted
    // song book must not be able to smuggle an arbitrary tag (or attribute) in through this field —
    // anything outside the closed set is dropped rather than rendered.
    private val ALLOWED_STYLES = setOf("u", "b", "i")

    private fun wrapStyle(text: String, styles: List<String>): String {
        var result = text
        for (style in styles.asReversed()) {
            if (style in ALLOWED_STYLES) {
                result = "<$style>$result</$style>"
            }
        }
        return result
    }

    private fun escapeHtml(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
