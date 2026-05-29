package yuku.alkitab.songs.document

import yuku.kpri.model.Song as LegacySong
import yuku.kpri.model.Lyric as LegacyLyric
import yuku.kpri.model.Verse as LegacyVerse
import yuku.kpri.model.VerseKind as LegacyVerseKind

/**
 * Converts legacy [LegacySong] instances (from `yuku.kpri.model`) into the canonical
 * [SongDocument] format defined in design.md §5.
 *
 * The conversion is lossless for display purposes: every field that affects rendering
 * is mapped to an ordered list of [Block]s that reproduces the current app layout.
 * Fields that do not affect rendering (e.g. [LegacyVerse.ordering]) are dropped.
 *
 * Inline HTML-style tags (`<u>`, `<b>`, `<i>`) in verse lines are parsed into [Span]s.
 * Nested tags are supported. Unknown tags are treated as raw text.
 */
object LegacySongConverter {

    /**
     * Convert a legacy [LegacySong] to a canonical [SongDocument].
     *
     * Block order mirrors the app `song.html` template:
     * title → title_original → tune → authors row → scripture → musical → lyric groups.
     *
     * [SongDocument.meta] is derived from the first blocks with role `"title"` and
     * `"title_original"`.
     *
     * Null or blank optional fields are skipped gracefully.
     */
    fun convert(song: LegacySong): SongDocument {
        val blocks = mutableListOf<Block>()

        // 1. title
        if (!song.title.isNullOrBlank()) {
            blocks.add(PBlock(role = "title", content = plainLine(song.title)))
        }

        // 2. title_original
        if (!song.title_original.isNullOrBlank()) {
            blocks.add(PBlock(role = "title_original", content = plainLine(song.title_original)))
        }

        // 3. tune
        if (!song.tune.isNullOrBlank()) {
            blocks.add(PBlock(role = "tune", content = plainLine(song.tune)))
        }

        // 4. authors → row block
        val authorsLyric = song.authors_lyric
            ?.filter { it.isNotBlank() }
            ?.joinToString("; ")
        val authorsMusic = song.authors_music
            ?.filter { it.isNotBlank() }
            ?.joinToString("; ")

        if (!authorsLyric.isNullOrBlank() || !authorsMusic.isNullOrBlank()) {
            val rowItems = mutableListOf<PBlock>()
            if (!authorsLyric.isNullOrBlank()) {
                rowItems.add(PBlock(role = "authors_lyric", content = plainLine(authorsLyric)))
            }
            if (!authorsMusic.isNullOrBlank()) {
                rowItems.add(PBlock(role = "authors_music", content = plainLine(authorsMusic)))
            }
            blocks.add(RowBlock(items = rowItems))
        }

        // 5. scriptureReferences
        if (!song.scriptureReferences.isNullOrBlank()) {
            blocks.add(ScriptureBlock(osis = song.scriptureReferences))
        }

        // 6. keySignature + timeSignature → musical
        val musicalParts = listOfNotNull(
            song.keySignature?.takeIf { it.isNotBlank() },
            song.timeSignature?.takeIf { it.isNotBlank() }
        )
        if (musicalParts.isNotEmpty()) {
            blocks.add(PBlock(role = "musical", content = plainLine(musicalParts.joinToString(" "))))
        }

        // 7. lyrics → lyric blocks
        song.lyrics?.forEach { lyric ->
            val captionLine = lyric.caption?.takeIf { it.isNotBlank() }?.let { plainLine(it) }
            val verses = lyric.verses?.map { verse ->
                val kind = mapVerseKind(verse.kind)
                val lines = verse.lines?.map { line ->
                    VerseLine(content = parseInlineStyles(line))
                } ?: emptyList()
                Verse(kind = kind, lines = lines)
            } ?: emptyList()

            blocks.add(LyricBlock(caption = captionLine, verses = verses))
        }

        // Derive meta from title / title_original blocks
        val meta = SongMeta(
            title = extractTextFromRole(blocks, "title"),
            title_original = extractTextFromRole(blocks, "title_original")
        )

        return SongDocument(
            code = song.code ?: "",
            meta = meta,
            blocks = blocks
        )
    }

    /**
     * Parse inline HTML-style tags (`<u>`, `<b>`, `<i>`) into spans.
     * Other text is preserved as plain spans.
     *
     * Tags may be nested. Malformed or unmatched tags are handled gracefully:
     * closing tags without a matching opener are ignored, and unclosed tags
     * apply to the remaining text.
     *
     * Examples:
     * - `"<u>Sia</u>pa yang b'lum lelap;"` → `[Span("Sia", ["u"]), Span("pa yang b'lum lelap;")]`
     * - `"<b><i>bold italic</i></b>"` → `[Span("bold italic", ["b", "i"])]`
     *
     * @param text The raw verse line text.
     * @return A [Line] (list of [Span]s) representing the styled text.
     */
    fun parseInlineStyles(text: String): Line {
        val tagPattern = Regex("</?[ubi]>")
        val matches = tagPattern.findAll(text).toList()

        // Fast path: no tags at all
        if (matches.isEmpty()) {
            return plainLine(text)
        }

        val result = mutableListOf<Span>()
        val activeStyles = mutableListOf<String>()
        var lastIndex = 0

        for (match in matches) {
            val beforeText = text.substring(lastIndex, match.range.first)

            if (beforeText.isNotEmpty()) {
                result.add(
                    Span(
                        text = beforeText,
                        style = activeStyles.toList().takeIf { it.isNotEmpty() }
                    )
                )
            }

            val tagText = match.value
            if (tagText.startsWith("</")) {
                // Closing tag: remove the matching style if present
                val tagName = tagText.substring(2, tagText.length - 1)
                activeStyles.remove(tagName)
            } else {
                // Opening tag: add to active styles
                val tagName = tagText.substring(1, tagText.length - 1)
                activeStyles.add(tagName)
            }

            lastIndex = match.range.last + 1
        }

        // Append any trailing text after the last tag
        val remaining = text.substring(lastIndex)
        if (remaining.isNotEmpty()) {
            result.add(
                Span(
                    text = remaining,
                    style = activeStyles.toList().takeIf { it.isNotEmpty() }
                )
            )
        }

        // Ensure we never return an empty line
        if (result.isEmpty()) {
            result.add(Span(text = "", style = null))
        }

        return result
    }

    /**
     * Map a legacy [LegacyVerseKind] to the document [VerseKind].
     * Defaults to [VerseKind.NORMAL] for null or unknown values.
     */
    private fun mapVerseKind(legacyKind: LegacyVerseKind?): VerseKind {
        return when (legacyKind) {
            LegacyVerseKind.NORMAL -> VerseKind.NORMAL
            LegacyVerseKind.REFRAIN -> VerseKind.REFRAIN
            LegacyVerseKind.TEXT -> VerseKind.TEXT
            null -> VerseKind.NORMAL
        }
    }

    /**
     * Extract plain text from the first [PBlock] with the given [role].
     * Returns `null` if no matching block is found.
     */
    private fun extractTextFromRole(blocks: List<Block>, role: String): String? {
        val block = blocks.filterIsInstance<PBlock>().find { it.role == role }
        return block?.content?.joinToString("") { it.text }
    }

    /**
     * Convert a canonical [SongDocument] back to a legacy [LegacySong].
     * Inverse of [convert].
     */
    fun convertToLegacy(document: SongDocument): LegacySong {
        val song = LegacySong()
        song.code = document.code

        for (block in document.blocks) {
            when (block) {
                is PBlock -> {
                    when (block.role) {
                        "title" -> song.title = extractText(block.content)
                        "title_original" -> song.title_original = extractText(block.content)
                        "tune" -> song.tune = extractText(block.content)
                        "musical" -> {
                            val text = extractText(block.content)
                            if (text != null) {
                                val parts = text.split(" ", limit = 2)
                                song.keySignature = parts.getOrNull(0)
                                if (parts.size > 1) {
                                    song.timeSignature = parts[1]
                                }
                            }
                        }
                    }
                }
                is RowBlock -> {
                    for (item in block.items) {
                        when (item.role) {
                            "authors_lyric" -> {
                                val text = extractText(item.content)
                                if (text != null) {
                                    song.authors_lyric = text.split("; ").filter { it.isNotBlank() }.toMutableList()
                                }
                            }
                            "authors_music" -> {
                                val text = extractText(item.content)
                                if (text != null) {
                                    song.authors_music = text.split("; ").filter { it.isNotBlank() }.toMutableList()
                                }
                            }
                        }
                    }
                }
                is ScriptureBlock -> {
                    song.scriptureReferences = block.osis
                }
                is LyricBlock -> {
                    val lyric = LegacyLyric()
                    lyric.caption = block.caption?.let { extractText(it) }
                    lyric.verses = block.verses.map { verse ->
                        val legacyVerse = LegacyVerse()
                        legacyVerse.kind = mapVerseKindReverse(verse.kind)
                        legacyVerse.lines = verse.lines.map { line ->
                            renderLineToString(line.content)
                        }.toMutableList()
                        legacyVerse
                    }.toMutableList()
                    if (song.lyrics == null) {
                        song.lyrics = ArrayList()
                    }
                    song.lyrics.add(lyric)
                }
                else -> {}
            }
        }

        return song
    }

    private fun extractText(line: Line): String? {
        return line.joinToString("") { it.text }.takeIf { it.isNotBlank() }
    }

    private fun renderLineToString(line: Line): String {
        if (line.isEmpty()) return ""
        if (line.size == 1 && line[0].style.isNullOrEmpty()) {
            return line[0].text
        }

        val sb = StringBuilder()
        for (span in line) {
            val styles = span.style ?: emptyList()
            for (style in styles) {
                sb.append("<$style>")
            }
            sb.append(span.text)
            for (style in styles.reversed()) {
                sb.append("</$style>")
            }
        }
        return sb.toString()
    }

    private fun mapVerseKindReverse(kind: VerseKind): LegacyVerseKind {
        return when (kind) {
            VerseKind.NORMAL -> LegacyVerseKind.NORMAL
            VerseKind.REFRAIN -> LegacyVerseKind.REFRAIN
            VerseKind.TEXT -> LegacyVerseKind.TEXT
        }
    }
}
