package yuku.alkitab.songs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.songs.newdoc.Line
import yuku.alkitab.songs.newdoc.LyricBlock
import yuku.alkitab.songs.newdoc.Meta
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.Verse as DocVerse
import yuku.alkitab.songs.newdoc.VerseKind as DocVerseKind
import yuku.alkitab.songs.newdoc.VerseLine
import yuku.kpri.model.Lyric
import yuku.kpri.model.Song
import yuku.kpri.model.Verse as KpriVerse

class SongFilterMatchTest {
    private fun songInfo(code: String, title: String, titleOriginal: String? = null) =
        SongInfo("KJ", code, title, titleOriginal)

    private fun song(
        code: String = "1",
        title: String = "Untitled",
        titleOriginal: String? = null,
        authorsLyric: List<String>? = null,
        authorsMusic: List<String>? = null,
        tune: String? = null,
        lyrics: List<Lyric?> = emptyList(),
    ) = Song().apply {
        this.code = code
        this.title = title
        this.title_original = titleOriginal
        this.authors_lyric = authorsLyric
        this.authors_music = authorsMusic
        this.tune = tune
        this.lyrics = lyrics
    }

    private fun lyric(vararg verses: List<String>) = Lyric().apply {
        this.verses = verses.map { lines -> KpriVerse().apply { this.lines = lines } }
    }

    private fun doc(code: String = "1", title: String? = null, vararg verses: List<String>) = SongDocument(
        code = code,
        meta = Meta(title = title),
        blocks = listOf(
            LyricBlock(
                verses = verses.map { lines ->
                    DocVerse(kind = DocVerseKind.NORMAL, lines = lines.map { VerseLine.Simple(Line.of(it)) })
                },
            ),
        ),
    )

    @Test
    fun `a null or blank filter compiles to one that matches everything`() {
        val anything = songInfo("99", "Anything")
        assertTrue(SongFilter.match(anything, SongFilter.compileFilter(null)))
        assertTrue(SongFilter.match(anything, SongFilter.compileFilter("")))
        assertTrue(SongFilter.match(anything, SongFilter.compileFilter("   ")))
    }

    @Test
    fun `filterSongInfosByString with a null filter returns every song in order`() {
        val infos = listOf(songInfo("2", "B"), songInfo("1", "A"))
        assertEquals(infos, SongFilter.filterSongInfosByString(infos, null))
    }

    @Test
    fun `filterSongInfosByString keeps songs whose code, title or original title match`() {
        val byCode = songInfo("123", "First")
        val byTitle = songInfo("5", "Amazing Grace")
        val byOriginalTitle = songInfo("6", "Kasih", "Amazing Love")
        val none = songInfo("7", "Other")

        val filtered = SongFilter.filterSongInfosByString(listOf(byCode, byTitle, byOriginalTitle, none), "amaz")
        assertEquals(listOf(byTitle, byOriginalTitle), filtered)
        assertEquals(listOf(byCode), SongFilter.filterSongInfosByString(listOf(byCode, byTitle, none), "12"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertTrue(SongFilter.match(songInfo("1", "Amazing Grace"), SongFilter.compileFilter("GRACE")))
    }

    @Test
    fun `every token must match, each possibly in a different field`() {
        val info = songInfo("12", "Amazing Grace")
        assertTrue(SongFilter.match(info, SongFilter.compileFilter("12 grace")))
        assertFalse(SongFilter.match(info, SongFilter.compileFilter("12 love")))
    }

    @Test
    fun `a quoted token only matches at word boundaries`() {
        val info = songInfo("1", "Beloved")
        assertTrue(SongFilter.match(info, SongFilter.compileFilter("love")))
        assertFalse(SongFilter.match(info, SongFilter.compileFilter("\"love\"")))
        assertTrue(SongFilter.match(songInfo("1", "My love song"), SongFilter.compileFilter("\"love\"")))
    }

    @Test
    fun `regex metacharacters in the filter are matched literally`() {
        assertFalse(SongFilter.match(songInfo("1", "axb"), SongFilter.compileFilter("a.b")))
        assertTrue(SongFilter.match(songInfo("1", "a.b"), SongFilter.compileFilter("a.b")))
    }

    @Test
    fun `a legacy song matches on authors, tune and lyric lines`() {
        assertTrue(SongFilter.match(song(authorsLyric = listOf("Fanny Crosby")), SongFilter.compileFilter("crosby")))
        assertTrue(SongFilter.match(song(authorsMusic = listOf("Lowell Mason")), SongFilter.compileFilter("mason")))
        assertTrue(SongFilter.match(song(tune = "HAMBURG"), SongFilter.compileFilter("hamburg")))
        assertTrue(SongFilter.match(song(lyrics = listOf(lyric(listOf("first line", "blessed assurance")))), SongFilter.compileFilter("assurance")))
        assertFalse(SongFilter.match(song(lyrics = listOf(lyric(listOf("first line")))), SongFilter.compileFilter("assurance")))
    }

    @Test
    fun `a legacy song skips null lyrics and null optional fields`() {
        val s = song(lyrics = listOf(null, lyric(listOf("found here"))))
        assertTrue(SongFilter.match(s, SongFilter.compileFilter("found")))
        assertFalse(SongFilter.match(song(), SongFilter.compileFilter("missing")))
    }

    @Test
    fun `a song document matches on code, title and lyric lines`() {
        assertTrue(SongFilter.match(doc(code = "KJ-12"), SongFilter.compileFilter("kj-12")))
        assertTrue(SongFilter.match(doc(title = "Holy"), SongFilter.compileFilter("holy")))
        assertTrue(SongFilter.match(doc("1", null, listOf("sing praise")), SongFilter.compileFilter("praise")))
        assertFalse(SongFilter.match(doc("1", null, listOf("sing praise")), SongFilter.compileFilter("amen")))
    }

    @Test
    fun `findLyricSnippet joins up to maxLines matching lyric lines`() {
        val d = doc("1", null, listOf("grace one", "nothing", "grace two"), listOf("grace three"))
        val cf = SongFilter.compileFilter("grace")
        assertEquals("grace one\ngrace two", SongFilter.findLyricSnippet(d, cf, 2))
        assertEquals("grace one\ngrace two\ngrace three", SongFilter.findLyricSnippet(d, cf, 5))
    }

    @Test
    fun `findLyricSnippet picks a line when any token matches it`() {
        val d = doc("1", null, listOf("alpha", "beta", "gamma"))
        assertEquals("alpha\ngamma", SongFilter.findLyricSnippet(d, SongFilter.compileFilter("alpha gamma"), 5))
    }

    @Test
    fun `findLyricSnippet returns null for an empty filter or when no lyric line matches`() {
        val d = doc("1", "Grace", listOf("nothing here"))
        assertNull(SongFilter.findLyricSnippet(d, SongFilter.compileFilter(""), 2))
        assertNull(SongFilter.findLyricSnippet(d, SongFilter.compileFilter("grace"), 2))
    }

    @Test
    fun `matchRanges returns nothing for null text`() {
        assertTrue(SongFilter.matchRanges(null, SongFilter.compileFilter("a")).isEmpty())
    }
}
