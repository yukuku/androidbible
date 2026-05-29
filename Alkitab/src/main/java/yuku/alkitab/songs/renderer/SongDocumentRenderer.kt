package yuku.alkitab.songs.renderer

import android.os.Bundle
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import yuku.alkitab.base.App
import yuku.alkitab.base.util.OsisBookNames
import yuku.alkitab.base.widget.Localized
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book
import yuku.alkitab.songs.SongBookUtil
import yuku.alkitab.songs.document.Block
import yuku.alkitab.songs.document.Line
import yuku.alkitab.songs.document.LyricBlock
import yuku.alkitab.songs.document.PBlock
import yuku.alkitab.songs.document.RowBlock
import yuku.alkitab.songs.document.ScriptureBlock
import yuku.alkitab.songs.document.SongDocument
import yuku.alkitab.songs.document.Span
import yuku.alkitab.songs.document.UnknownBlock
import yuku.alkitab.songs.document.Verse
import yuku.alkitab.songs.document.VerseKind
import yuku.alkitab.songs.document.YoutubeBlock

/**
 * Renders a [SongDocument] to HTML (for WebView display) or plain text (for copy/share).
 *
 * The HTML output mirrors the app's `song.html` template structure, using the same CSS classes
 * defined in `song.css`. Scripture OSIS references are localized at render time against the
 * user's active Bible version.
 */
object SongDocumentRenderer {

    /**
     * Render a [SongDocument] to HTML string for WebView display.
     *
     * @param document The song document to render
     * @param code The song code (for display)
     * @param customVars Optional custom template variables (e.g. background color, text color)
     * @param forPatchText If true, render simplified HTML suitable for the patch-text feature
     * @return HTML string
     */
    fun renderToHtml(
        document: SongDocument,
        code: String,
        customVars: Bundle? = null,
        forPatchText: Boolean = false
    ): String {
        if (forPatchText) {
            return renderLyricsToSimplifiedHtml(document.blocks)
        }

        val template = loadTemplate()

        // Extract blocks by role / type
        val title = findPBlockByRole(document.blocks, "title")
        val titleOriginal = findPBlockByRole(document.blocks, "title_original")
        val tune = findPBlockByRole(document.blocks, "tune")
        val musical = findPBlockByRole(document.blocks, "musical")
        val scriptureBlock = findScriptureBlock(document.blocks)
        val (authorsLyric, authorsMusic) = findAuthors(document.blocks)

        // Split musical into keySignature / timeSignature
        val (keySignature, timeSignature) = splitMusical(musical)

        // Localize scripture references
        val scriptureReferences = scriptureBlock?.let {
            localizeScriptureToHtml(it.osis)
        } ?: ""

        // Render lyrics
        val lyricsHtml = renderLyricsToHtml(document.blocks)

        val extraHtml = renderNonTemplateBlocks(document.blocks)

        // Build result by replacing template placeholders
        var result = template
        result = templateDivReplace(result, "code", code)
        result = templateDivReplace(result, "title", renderLineToHtml(title?.content))
        result = templateDivReplace(result, "title_original", renderLineToHtml(titleOriginal?.content))
        result = templateDivReplace(result, "tune", renderLineToHtml(tune?.content))
        result = templateDivReplace(result, "authors_lyric", renderLineToHtml(authorsLyric?.content))
        result = templateDivReplace(result, "authors_music", renderLineToHtml(authorsMusic?.content))
        result = templateVarReplace(result, "scripture_references", scriptureReferences)
        result = templateDivReplace(result, "keySignature", keySignature)
        result = templateDivReplace(result, "timeSignature", timeSignature)
        result = templateDivReplace(result, "lyrics", lyricsHtml + extraHtml)

        // Apply custom template variables
        customVars?.let {
            for (key in it.keySet()) {
                result = templateVarReplace(result, key, it.getString(key))
            }
        }

        return result
    }

    /**
     * Render a [SongDocument] to plain text for copy/share.
     *
     * @param document The song document to render
     * @param bookName Optional song book name (prepended to output)
     * @return Plain text [StringBuilder]
     */
    fun renderToText(document: SongDocument, bookName: String? = null): StringBuilder {
        val sb = StringBuilder()

        // Header: book name + code + title
        if (bookName != null) {
            sb.append(SongBookUtil.escapeSongBookName(bookName)).append(' ')
        }
        sb.append(document.code).append(". ")
        val title = findPBlockByRole(document.blocks, "title")
        sb.append(renderLineToText(title?.content)).append('\n')

        val titleOriginal = findPBlockByRole(document.blocks, "title_original")
        if (titleOriginal != null) {
            sb.append('(').append(renderLineToText(titleOriginal.content)).append(')').append('\n')
        }
        sb.append('\n')

        // Authors
        val authorsLyric = findPBlockByRole(document.blocks, "authors_lyric")
        if (authorsLyric != null) {
            sb.append(renderLineToText(authorsLyric.content)).append('\n')
        }
        val authorsMusic = findPBlockByRole(document.blocks, "authors_music")
        if (authorsMusic != null) {
            sb.append(renderLineToText(authorsMusic.content)).append('\n')
        }

        // Tune
        val tune = findPBlockByRole(document.blocks, "tune")
        if (tune != null) {
            sb.append(renderLineToText(tune.content).uppercase(Locale.getDefault())).append('\n')
        }
        sb.append('\n')

        // Scripture references
        val scriptureBlock = findScriptureBlock(document.blocks)
        if (scriptureBlock != null) {
            sb.append(localizeScriptureToText(scriptureBlock.osis)).append('\n')
        }

        // Musical (key + time signature)
        val musical = findPBlockByRole(document.blocks, "musical")
        if (musical != null) {
            sb.append(renderLineToText(musical.content)).append('\n')
        }
        sb.append('\n')

        // Lyrics
        val lyricBlocks = document.blocks.filterIsInstance<LyricBlock>()
        for (i in lyricBlocks.indices) {
            val lyric = lyricBlocks[i]

            // Caption fallback
            if (lyricBlocks.size > 1 || lyric.caption != null) {
                if (lyric.caption != null) {
                    sb.append(renderLineToText(lyric.caption)).append('\n')
                } else {
                    sb.append(Localized.string(R.string.sn_lyric_version_version, (i + 1).toString())).append('\n')
                }
            }

            var verseNormalNo = 0
            for (verse in lyric.verses) {
                if (verse.kind == VerseKind.NORMAL) {
                    verseNormalNo++
                }

                var skipPad = false
                if (verse.kind == VerseKind.REFRAIN) {
                    sb.append(Localized.string(R.string.sn_lyric_refrain_marker)).append('\n')
                } else {
                    sb.append(String.format(Locale.US, "%2d: ", verseNormalNo))
                    skipPad = true
                }

                for (line in verse.lines) {
                    if (!skipPad) {
                        sb.append("    ")
                    } else {
                        skipPad = false
                    }
                    sb.append(renderLineToText(line.content)).append('\n')
                }
                sb.append('\n')
            }
            sb.append('\n')
        }

        for (block in document.blocks) {
            if (block is UnknownBlock) {
                val text = block.rawJson["content"]?.jsonPrimitive?.content
                    ?: block.rawJson["text"]?.jsonPrimitive?.content
                    ?: ""
                sb.append(text)
                sb.append('\n')
            }
        }

        return sb
    }

    // ---------------------------------------------------------------------------------------------
    // Template helpers
    // ---------------------------------------------------------------------------------------------

    private fun loadTemplate(): String {
        return App.context.assets.open("templates/song.html").use { input ->
            input.reader().readText()
        }
    }

    private fun templateDivReplace(template: String, name: String, value: String?): String {
        return template.replace("{{div:$name}}", if (value == null) "" else "<div class='$name'>$value</div>")
    }

    private fun templateVarReplace(template: String, name: String, value: Any?): String {
        return template.replace("{{\$$name}}", value?.toString() ?: "")
    }

    // ---------------------------------------------------------------------------------------------
    // Block extraction
    // ---------------------------------------------------------------------------------------------

    private fun findPBlockByRole(blocks: List<Block>, role: String): PBlock? {
        return blocks.filterIsInstance<PBlock>().find { it.role == role }
    }

    private fun findScriptureBlock(blocks: List<Block>): ScriptureBlock? {
        return blocks.filterIsInstance<ScriptureBlock>().firstOrNull()
    }

    private fun findAuthors(blocks: List<Block>): Pair<PBlock?, PBlock?> {
        for (block in blocks) {
            if (block is RowBlock) {
                val lyric = block.items.find { it.role == "authors_lyric" }
                val music = block.items.find { it.role == "authors_music" }
                if (lyric != null || music != null) {
                    return lyric to music
                }
            }
        }
        return null to null
    }

    private fun splitMusical(musical: PBlock?): Pair<String?, String?> {
        if (musical == null) return null to null
        val content = renderLineToText(musical.content)
        val firstSpace = content.indexOf(' ')
        return if (firstSpace == -1) {
            content to null
        } else {
            content.substring(0, firstSpace) to content.substring(firstSpace + 1)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lyric rendering (full HTML)
    // ---------------------------------------------------------------------------------------------

    private fun renderLyricsToHtml(blocks: List<Block>): String {
        val lyricBlocks = blocks.filterIsInstance<LyricBlock>()
        val sb = StringBuilder()
        for (i in lyricBlocks.indices) {
            val lyric = lyricBlocks[i]
            sb.append("<div class='lyric'>")

            // Caption fallback
            if (lyricBlocks.size > 1 || lyric.caption != null) {
                if (lyric.caption != null) {
                    sb.append("<div class='lyric_caption'>")
                        .append(renderLineToHtml(lyric.caption))
                        .append("</div>")
                } else {
                    sb.append("<div class='lyric_caption'>")
                        .append(Localized.string(R.string.sn_lyric_version_version, (i + 1).toString()))
                        .append("</div>")
                }
            }

            var verseNumberNormal = 0
            var verseNumberReff = 0
            for (verse in lyric.verses) {
                sb.append("<div class='verse")
                if (verse.kind == VerseKind.REFRAIN) sb.append(" refrain")
                sb.append("'>")

                when (verse.kind) {
                    VerseKind.REFRAIN -> verseNumberReff++
                    VerseKind.NORMAL -> verseNumberNormal++
                    else -> {}
                }

                when (verse.kind) {
                    VerseKind.REFRAIN -> {
                        sb.append("<div class='verse_ordering'>")
                            .append(verseNumberReff)
                            .append("</div>")
                    }
                    VerseKind.NORMAL -> {
                        sb.append("<div class='verse_ordering'>")
                            .append(verseNumberNormal)
                            .append("</div>")
                    }
                    else -> {}
                }

                sb.append("<div class='verse_content'>")
                for (line in verse.lines) {
                    sb.append("<p class='line'>")
                    sb.append(renderLineToHtml(line.content))
                    sb.append("</p>")
                }
                sb.append("</div>")
                sb.append("</div>")
            }
            sb.append("</div>")
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------------------------------------
    // Lyric rendering (simplified HTML for patch text)
    // ---------------------------------------------------------------------------------------------

    private fun renderLyricsToSimplifiedHtml(blocks: List<Block>): String {
        val lyricBlocks = blocks.filterIsInstance<LyricBlock>()
        val sb = StringBuilder()
        for (i in lyricBlocks.indices) {
            val lyric = lyricBlocks[i]
            sb.append("<div class='lyric'>")

            if (lyricBlocks.size > 1 || lyric.caption != null) {
                if (lyric.caption != null) {
                    sb.append("<div class='lyric_caption'>")
                        .append(renderLineToText(lyric.caption))
                        .append("</div>")
                } else {
                    sb.append("<div class='lyric_caption'>")
                        .append(Localized.string(R.string.sn_lyric_version_version, (i + 1).toString()))
                        .append("</div>")
                }
            }

            var verseNumberNormal = 0
            var verseNumberReff = 0
            for (verse in lyric.verses) {
                sb.append("<div class='verse")
                if (verse.kind == VerseKind.REFRAIN) sb.append(" refrain")
                sb.append("'>")

                when (verse.kind) {
                    VerseKind.REFRAIN -> verseNumberReff++
                    VerseKind.NORMAL -> verseNumberNormal++
                    else -> {}
                }

                when (verse.kind) {
                    VerseKind.REFRAIN -> sb.append("reff ").append(verseNumberReff)
                    VerseKind.NORMAL -> sb.append(verseNumberNormal)
                    else -> {}
                }

                sb.append("<div class='verse_content'>")
                for (line in verse.lines) {
                    sb.append(renderLineToText(line.content)).append("<br/>")
                }
                sb.append("</div>")
                sb.append("</div>")
            }
            sb.append("</div>")
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------------------------------------
    // Non-template block rendering (appended after lyrics)
    // ---------------------------------------------------------------------------------------------

    private fun renderNonTemplateBlocks(blocks: List<Block>): String {
        val sb = StringBuilder()
        for (block in blocks) {
            when (block) {
                is PBlock -> {
                    if (block.role !in TEMPLATE_ROLES) {
                        sb.append(renderPBlockToHtml(block))
                    }
                }
                is RowBlock -> {
                    if (!block.items.any { it.role in TEMPLATE_ROLES }) {
                        sb.append(renderRowBlockToHtml(block))
                    }
                }
                is YoutubeBlock -> sb.append(renderYoutubeBlockToHtml(block))
                is ScriptureBlock -> {
                    if (block != findScriptureBlock(blocks)) {
                        sb.append(renderScriptureBlockToHtml(block))
                    }
                }
                is LyricBlock -> {} // already rendered
                is UnknownBlock -> {
                    sb.append("<div class='p unknown_block'>")
                    val text = block.rawJson["content"]?.jsonPrimitive?.content
                        ?: block.rawJson["text"]?.jsonPrimitive?.content
                        ?: ""
                    sb.append(htmlEscape(text))
                    sb.append("</div>")
                }
            }
        }
        return sb.toString()
    }

    private val TEMPLATE_ROLES = setOf("title", "title_original", "tune", "authors_lyric", "authors_music", "musical")

    private fun renderPBlockToHtml(block: PBlock): String {
        val roleClass = block.role?.let { " $it" } ?: ""
        val style = buildString {
            if (block.size != null) append("font-size:${(block.size * 100).toInt()}%;")
            if (block.align != null) append("text-align:${block.align};")
        }
        val styleAttr = if (style.isNotEmpty()) " style='$style'" else ""
        return "<div class='p$roleClass'$styleAttr>${renderLineToHtml(block.content)}</div>"
    }

    private fun renderRowBlockToHtml(block: RowBlock): String {
        val itemsHtml = block.items.joinToString("") { renderPBlockToHtml(it) }
        return "<div class='row'>$itemsHtml</div>"
    }

    private fun renderScriptureBlockToHtml(block: ScriptureBlock): String {
        return "<div class='scripture'>${localizeScriptureToHtml(block.osis)}</div>"
    }

    private fun renderYoutubeBlockToHtml(block: YoutubeBlock): String {
        return "<div class='youtube'><a href='https://youtube.com/watch?v=${htmlEscape(block.videoId)}'>${htmlEscape(block.videoId)}</a></div>"
    }

    // ---------------------------------------------------------------------------------------------
    // Line / Span rendering
    // ---------------------------------------------------------------------------------------------

    private fun renderLineToHtml(line: Line?): String {
        if (line == null) return ""
        return line.joinToString("") { renderSpanToHtml(it) }
    }

    private fun renderSpanToHtml(span: Span): String {
        val text = htmlEscape(span.text)
        val styles = span.style ?: return text
        var result = text
        // Apply styles in consistent order: u, b, i
        if ("u" in styles) result = "<u>$result</u>"
        if ("b" in styles) result = "<b>$result</b>"
        if ("i" in styles) result = "<i>$result</i>"
        return result
    }

    private fun renderLineToText(line: Line?): String {
        if (line == null) return ""
        return line.joinToString("") { it.text }
    }

    private fun htmlEscape(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    // ---------------------------------------------------------------------------------------------
    // Scripture localization
    // ---------------------------------------------------------------------------------------------

    private fun localizeScriptureToHtml(osis: String): String {
        return renderScriptureReferences("bible", osis)
    }

    private fun localizeScriptureToText(osis: String): String {
        return renderScriptureReferences(null, osis)
    }

    /**
     * Convert scripture ref lines like
     * B1.C1.V1-B2.C2.V2; B3.C3.V3 to localized readable text.
     *
     * @param protocol null to output plain text, non-null to wrap in `<a href='protocol:osisId'>`
     * @param line scripture ref in osis
     */
    private fun renderScriptureReferences(protocol: String?, line: String): String {
        if (line.isBlank()) return ""

        val sb = StringBuilder()
        val ranges = line.split("\\s*;\\s*".toRegex()).filter { it.isNotEmpty() }

        for (range in ranges) {
            val osisIds = if (range.indexOf('-') >= 0) {
                range.split("\\s*-\\s*".toRegex()).filter { it.isNotEmpty() }
            } else {
                listOf(range)
            }

            if (osisIds.size == 1) {
                if (sb.isNotEmpty()) sb.append("; ")
                val osisId = osisIds[0]
                val readable = osisIdToReadable(line, osisId, null, null)
                if (readable != null) {
                    appendScriptureReferenceLink(sb, protocol, osisId, readable)
                }
            } else if (osisIds.size == 2) {
                if (sb.isNotEmpty()) sb.append("; ")
                val bcv = intArrayOf(-1, 0, 0)
                val osisId0 = osisIds[0]
                val readable0 = osisIdToReadable(line, osisId0, null, bcv)
                val osisId1 = osisIds[1]
                val readable1 = osisIdToReadable(line, osisId1, bcv, null)
                if (readable0 != null && readable1 != null) {
                    appendScriptureReferenceLink(sb, protocol, "$osisId0-$osisId1", "$readable0-$readable1")
                }
            }
        }

        return sb.toString()
    }

    private fun appendScriptureReferenceLink(sb: StringBuilder, protocol: String?, osisId: String, readable: String) {
        if (protocol != null) {
            sb.append("<a href='").append(protocol).append(':').append(osisId).append("'>")
        }
        sb.append(readable)
        if (protocol != null) {
            sb.append("</a>")
        }
    }

    /**
     * @param compareWithRangeStart if this is the second part of a range, set this to non-null,
     *        with [0] is bookId and [1] chapter_1.
     * @param outBcv if not null and length is >= 3, will be filled with parsed bcv
     */
    private fun osisIdToReadable(
        line: String,
        osisId: String,
        compareWithRangeStart: IntArray?,
        outBcv: IntArray?
    ): String? {
        val parts = osisId.split("\\.".toRegex()).filter { it.isNotEmpty() }
        if (parts.size != 2 && parts.size != 3) {
            return null
        }

        val bookName = parts[0]
        val chapter_1 = parts[1].toIntOrNull() ?: return null
        val verse_1 = if (parts.size < 3) 0 else parts[2].toIntOrNull() ?: return null

        val bookId = OsisBookNames.osisBookNameToBookId(bookName)

        if (outBcv != null && outBcv.size >= 3) {
            outBcv[0] = bookId
            outBcv[1] = chapter_1
            outBcv[2] = verse_1
        }

        if (bookId < 0) return null

        val book: Book = App.services.versions.activeVersion().getBook(bookId) ?: return null

        var full = true
        if (compareWithRangeStart != null) {
            if (compareWithRangeStart[0] == bookId) {
                if (compareWithRangeStart[1] == chapter_1) {
                    return verse_1.toString()
                } else {
                    return "$chapter_1:$verse_1"
                }
            }
        }

        return if (verse_1 == 0) {
            book.reference(chapter_1)
        } else {
            book.reference(chapter_1, verse_1)
        }
    }
}
