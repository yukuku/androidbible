package yuku.alkitab.songs

import java.util.regex.Matcher
import java.util.regex.Pattern
import yuku.alkitab.base.util.QueryTokenizer
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.SongDocumentSearch
import yuku.kpri.model.Song

object SongFilter {
    class CompiledFilter internal constructor(internal val ps: Array<Pattern>?)

    @JvmStatic
    fun compileFilter(filterString: String?): CompiledFilter {
        if (filterString == null || filterString.trim { it <= ' ' }.isEmpty()) {
            return CompiledFilter(null)
        }

        val ps = QueryTokenizer.tokenize(filterString).map { token ->
            if (QueryTokenizer.isPlussedToken(token)) {
                Pattern.compile("\\b" + Pattern.quote(QueryTokenizer.tokenWithoutPlus(token)) + "\\b", Pattern.CASE_INSENSITIVE)
            } else {
                Pattern.compile(Pattern.quote(token), Pattern.CASE_INSENSITIVE)
            }
        }
        return CompiledFilter(ps.toTypedArray())
    }

    @JvmStatic
    fun filterSongInfosByString(songInfos: List<SongInfo>, filterString: String?): List<SongInfo> {
        if (filterString == null) return ArrayList(songInfos)

        val cf = compileFilter(filterString)
        return songInfos.filter { match(it, cf) }
    }

    private inline fun matchesAll(cf: CompiledFilter, matches: (Pattern) -> Boolean): Boolean {
        val ps = cf.ps ?: return true // empty filter? consider it passes
        return ps.all(matches)
    }

    @JvmStatic
    fun match(song: SongInfo, cf: CompiledFilter): Boolean = matchesAll(cf) { match(song, it) }

    @JvmStatic
    fun match(song: Song, cf: CompiledFilter): Boolean = matchesAll(cf) { match(song, it) }

    private fun match(song: SongInfo, p: Pattern): Boolean {
        val m = p.matcher("")

        if (find(song.code, m)) return true
        if (find(song.title, m)) return true
        return song.title_original != null && find(song.title_original, m)
    }

    private fun match(song: Song, p: Pattern): Boolean {
        val m = p.matcher("")

        if (find(song.code, m)) return true
        if (find(song.title, m)) return true
        if (song.title_original != null && find(song.title_original, m)) return true
        if (song.authors_lyric?.any { find(it, m) } == true) return true
        if (song.authors_music?.any { find(it, m) } == true) return true
        if (song.tune != null && find(song.tune, m)) return true

        return song.lyrics.filterNotNull().any { lyric ->
            lyric.verses.any { verse -> verse.lines.any { line -> find(line, m) } }
        }
    }

    /**
     * Mirrors `match(Song, CompiledFilter)` semantics for the new document model: scans
     * `code`, `meta.title`, `meta.title_original`, and the fields
     * [SongDocumentSearch.searchableTexts] extracts.
     */
    @JvmStatic
    fun match(doc: SongDocument, cf: CompiledFilter): Boolean = matchesAll(cf) { match(doc, it) }

    private fun match(doc: SongDocument, p: Pattern): Boolean {
        val m = p.matcher("")
        return SongDocumentSearch.searchableTexts(doc).any { find(it, m) }
    }

    private fun find(s: CharSequence, m: Matcher): Boolean {
        m.reset(s)
        return m.find()
    }

    /**
     * All [start, end) ranges in `text` that any filter token matches, so the search UI can
     * highlight the matched substrings the same way the verse search highlights hits. Ranges may
     * overlap when tokens overlap; callers that render spans (e.g. Compose AnnotatedString) handle
     * that fine. Returns an empty list for an empty filter.
     */
    @JvmStatic
    fun matchRanges(text: CharSequence?, cf: CompiledFilter): List<IntArray> {
        val ps = cf.ps
        if (text == null || ps == null) return emptyList()

        val res = mutableListOf<IntArray>()
        for (p in ps) {
            val m = p.matcher(text)
            while (m.find()) {
                if (m.end() > m.start()) res.add(intArrayOf(m.start(), m.end()))
            }
        }
        return res
    }

    /**
     * Up to `maxLines` lyric lines of `doc` that contain any filter token, joined by
     * `'\n'`, to preview where a deep-search hit occurred. Returns null when the filter is
     * empty or no lyric line matches (e.g. the hit was only in the title/authors, already shown).
     */
    @JvmStatic
    fun findLyricSnippet(doc: SongDocument, cf: CompiledFilter, maxLines: Int): String? {
        val ps = cf.ps ?: return null

        val picked = mutableListOf<String>()
        for (line in SongDocumentSearch.lyricLines(doc)) {
            if (ps.any { it.matcher(line).find() }) {
                picked.add(line)
                if (picked.size >= maxLines) break
            }
        }

        if (picked.isEmpty()) return null
        return picked.joinToString("\n")
    }
}
