package yuku.alkitab.songs.newdoc

import yuku.kpri.model.Lyric as LegacyLyric
import yuku.kpri.model.Song as LegacySong
import yuku.kpri.model.Verse as LegacyVerse
import yuku.kpri.model.VerseKind as LegacyVerseKind

/**
 * Maps a legacy [LegacySong] to a canonical [SongDocument], in the block
 * order that reproduces the current app layout (design §5 / §9): title,
 * title_original, tune, authors `row`, scripture, musical, lyric groups.
 */
object LegacySongConverter {
    @JvmStatic
    fun convert(song: LegacySong): SongDocument {
        val blocks = mutableListOf<Block>()

        song.title?.let { blocks.add(PBlock(role = "title", content = parseInlineLine(it))) }
        song.title_original?.let { blocks.add(PBlock(role = "title_original", content = parseInlineLine(it))) }
        song.tune?.let { blocks.add(PBlock(role = "tune", content = parseInlineLine(it))) }

        val authorsLyric = song.authors_lyric?.filterNotNull()?.takeIf { it.isNotEmpty() }?.joinToString("; ")
        val authorsMusic = song.authors_music?.filterNotNull()?.takeIf { it.isNotEmpty() }?.joinToString("; ")
        if (authorsLyric != null || authorsMusic != null) {
            val items = mutableListOf<PBlock>()
            authorsLyric?.let { items.add(PBlock(role = "authors_lyric", content = parseInlineLine(it))) }
            authorsMusic?.let { items.add(PBlock(role = "authors_music", content = parseInlineLine(it))) }
            blocks.add(RowBlock(items = items))
        }

        song.scriptureReferences?.let { blocks.add(ScriptureBlock(osis = it)) }

        val musical = listOfNotNull(song.keySignature, song.timeSignature).takeIf { it.isNotEmpty() }?.joinToString(" ")
        musical?.let { blocks.add(PBlock(role = "musical", content = parseInlineLine(it))) }

        song.lyrics?.filterNotNull()?.forEach { lyric -> blocks.add(convertLyric(lyric)) }

        return SongDocument(
            code = song.code ?: "",
            meta = SongDocumentJson.deriveMeta(blocks),
            blocks = blocks,
        )
    }

    private fun convertLyric(lyric: LegacyLyric): LyricBlock {
        val verses = lyric.verses.orEmpty().filterNotNull().map(::convertVerse)
        return LyricBlock(
            caption = lyric.caption?.let { parseInlineLine(it) },
            verses = verses,
        )
    }

    private fun convertVerse(verse: LegacyVerse): Verse {
        return Verse(
            kind = when (verse.kind) {
                LegacyVerseKind.NORMAL -> VerseKind.NORMAL
                LegacyVerseKind.REFRAIN -> VerseKind.REFRAIN
                LegacyVerseKind.TEXT -> VerseKind.TEXT
                null -> VerseKind.NORMAL
            },
            marker = null, // legacy verses have no explicit marker; numbering is positional (design §3.3)
            lines = verse.lines.orEmpty().filterNotNull().map { VerseLine.Simple(parseInlineLine(it)) },
        )
    }

    private val TAG_REGEX = Regex("<(u|b|i)>(.*?)</\\1>")

    /**
     * Parses legacy inline `<u>/<b>/<i>` markup (the only tags the legacy
     * HTML renderer understood) into [Span]s, and un-escapes the HTML
     * entities legacy line content used for literal `&`/`<`/`>` (design
     * §5 step 7: "other raw text escaped").
     */
    fun parseInlineLine(raw: String): Line {
        val spans = mutableListOf<Span>()
        var lastEnd = 0
        for (m in TAG_REGEX.findAll(raw)) {
            if (m.range.first > lastEnd) {
                val plain = raw.substring(lastEnd, m.range.first)
                if (plain.isNotEmpty()) spans.add(Span(text = unescapeEntities(plain)))
            }
            spans.add(Span(text = unescapeEntities(m.groupValues[2]), style = listOf(m.groupValues[1])))
            lastEnd = m.range.last + 1
        }
        if (lastEnd < raw.length) {
            val plain = raw.substring(lastEnd)
            if (plain.isNotEmpty()) spans.add(Span(text = unescapeEntities(plain)))
        }
        return when {
            spans.isEmpty() -> Line.Plain(unescapeEntities(raw))
            spans.size == 1 && spans[0].style.isEmpty() -> Line.Plain(spans[0].text)
            else -> Line.Styled(spans)
        }
    }

    private fun unescapeEntities(s: String): String {
        return s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&") // must be last, so it doesn't re-unescape "&amp;lt;" etc.
    }
}
