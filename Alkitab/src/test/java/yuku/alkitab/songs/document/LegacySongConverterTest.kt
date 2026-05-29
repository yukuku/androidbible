package yuku.alkitab.songs.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.songs.document.LegacySongConverter.convert
import yuku.alkitab.songs.document.LegacySongConverter.convertToLegacy
import yuku.alkitab.songs.document.LegacySongConverter.parseInlineStyles
import yuku.kpri.model.Lyric
import yuku.kpri.model.Song
import yuku.kpri.model.Verse
import yuku.kpri.model.VerseKind as LegacyVerseKind
import yuku.alkitab.songs.document.VerseKind
import yuku.alkitab.songs.document.Verse as DocVerse

/**
 * Unit tests for [LegacySongConverter], covering both [convert] (legacy → document)
 * and [convertToLegacy] (document → legacy) directions.
 *
 * All tests are pure-JVM — no Android dependencies, no Robolectric.
 */
class LegacySongConverterTest {

    // ============================================================================================
    // convert() — full field mapping
    // ============================================================================================

    @Test
    fun `convert maps all legacy fields correctly`() {
        val song = Song()
        song.code = "042"
        song.title = "Malam Kudus"
        song.title_original = "Silent Night"
        song.tune = "Stille Nacht"
        song.authors_lyric = listOf("Joseph Mohr")
        song.authors_music = listOf("Franz X. Gruber")
        song.keySignature = "Bes"
        song.timeSignature = "3/4"
        song.scriptureReferences = "Luke.2.14"

        val verse1 = Verse()
        verse1.kind = LegacyVerseKind.NORMAL
        verse1.lines = mutableListOf("Malam Kudus, sunyi senyap,", "Sia<sup>pa</sup> yang b'lum lelap;")

        val verse2 = Verse()
        verse2.kind = LegacyVerseKind.REFRAIN
        verse2.lines = mutableListOf("Mengíngat Dia, slaislah b'doa;")

        val lyric = Lyric()
        lyric.caption = "Versi 1"
        lyric.verses = mutableListOf(verse1, verse2)

        song.lyrics = mutableListOf(lyric)

        val doc = convert(song)

        // code
        assertEquals("042", doc.code)

        // meta derived from title blocks
        assertEquals("Malam Kudus", doc.meta?.title)
        assertEquals("Silent Night", doc.meta?.title_original)

        // blocks in expected order: title, title_original, tune, authors row, scripture, musical, lyric
        assertEquals(7, doc.blocks.size)

        // 1. title PBlock
        val titleBlock = doc.blocks[0] as PBlock
        assertEquals("title", titleBlock.role)
        assertEquals("Malam Kudus", titleBlock.content.joinText())

        // 2. title_original PBlock
        val titleOrigBlock = doc.blocks[1] as PBlock
        assertEquals("title_original", titleOrigBlock.role)
        assertEquals("Silent Night", titleOrigBlock.content.joinText())

        // 3. tune PBlock
        val tuneBlock = doc.blocks[2] as PBlock
        assertEquals("tune", tuneBlock.role)
        assertEquals("Stille Nacht", tuneBlock.content.joinText())

        // 4. authors RowBlock
        val authorsRow = doc.blocks[3] as RowBlock
        assertEquals(2, authorsRow.items.size)
        assertEquals("authors_lyric", authorsRow.items[0].role)
        assertEquals("Joseph Mohr", authorsRow.items[0].content.joinText())
        assertEquals("authors_music", authorsRow.items[1].role)
        assertEquals("Franz X. Gruber", authorsRow.items[1].content.joinText())

        // 5. scripture block
        val scriptureBlock = doc.blocks[4] as ScriptureBlock
        assertEquals("Luke.2.14", scriptureBlock.osis)

        // 6. musical PBlock
        val musicalBlock = doc.blocks[5] as PBlock
        assertEquals("musical", musicalBlock.role)
        assertEquals("Bes 3/4", musicalBlock.content.joinText())

        // 7. lyric block
        val lyricBlock = doc.blocks[6] as LyricBlock
        assertEquals("Versi 1", lyricBlock.caption?.joinText())
        assertEquals(2, lyricBlock.verses.size)

        val v1 = lyricBlock.verses[0]
        assertEquals(VerseKind.NORMAL, v1.kind)

        val v2 = lyricBlock.verses[1]
        assertEquals(VerseKind.REFRAIN, v2.kind)
    }

    // ============================================================================================
    // convert() — null/empty field handling
    // ============================================================================================

    @Test
    fun `convert handles null fields gracefully`() {
        val song = Song()
        song.code = "001"
        song.title = "Hanya Yesus"

        val doc = convert(song)

        assertEquals("001", doc.code)
        assertEquals("Hanya Yesus", doc.meta?.title)
        assertNull(doc.meta?.title_original)

        // Only title block should exist — no crashes
        val titleBlocks = doc.blocks.filterIsInstance<PBlock>().filter { it.role == "title" }
        assertEquals(1, titleBlocks.size)

        // No other blocks
        assertEquals(1, doc.blocks.size)
    }

    @Test
    fun `convert handles empty lists gracefully`() {
        val song = Song()
        song.code = "99"
        song.title = "Empty Song"
        song.authors_lyric = emptyList()
        song.authors_music = emptyList()
        song.lyrics = mutableListOf(Lyric()) // empty lyric

        val doc = convert(song)

        // No RowBlock should be emitted for empty author lists
        val rowBlocks = doc.blocks.filterIsInstance<RowBlock>()
        assertTrue(rowBlocks.isEmpty())

        // Empty lyric produces LyricBlock with empty verses
        val lyricBlocks = doc.blocks.filterIsInstance<LyricBlock>()
        assertEquals(1, lyricBlocks.size)
        assertTrue(lyricBlocks[0].verses.isEmpty())
    }

    // ============================================================================================
    // parseInlineStyles()
    // ============================================================================================

    @Test
    fun `parseInlineStyles handles u tag`() {
        val line = parseInlineStyles("<u>Sia</u>pa yang b'lum lelap;")
        assertEquals(2, line.size)
        assertEquals("Sia", line[0].text)
        assertEquals(listOf("u"), line[0].style)
        assertEquals("pa yang b'lum lelap;", line[1].text)
        assertNull(line[1].style)
    }

    @Test
    fun `parseInlineStyles handles b tag`() {
        val line = parseInlineStyles("<b>bold text</b> after")
        assertEquals(2, line.size)
        assertEquals("bold text", line[0].text)
        assertEquals(listOf("b"), line[0].style)
        assertEquals(" after", line[1].text)
        assertNull(line[1].style)
    }

    @Test
    fun `parseInlineStyles handles i tag`() {
        val line = parseInlineStyles("<i>italic text</i> after")
        assertEquals(2, line.size)
        assertEquals("italic text", line[0].text)
        assertEquals(listOf("i"), line[0].style)
        assertEquals(" after", line[1].text)
        assertNull(line[1].style)
    }

    @Test
    fun `parseInlineStyles handles nested b and i`() {
        val line = parseInlineStyles("<b><i>bold italic</i></b>")
        assertEquals(1, line.size)
        assertEquals("bold italic", line[0].text)
        assertEquals(listOf("b", "i"), line[0].style)
    }

    @Test
    fun `parseInlineStyles handles mixed styled and plain`() {
        val line = parseInlineStyles("<u>Sia</u>pa")
        assertEquals(2, line.size)
        assertEquals("Sia", line[0].text)
        assertEquals(listOf("u"), line[0].style)
        assertEquals("pa", line[1].text)
        assertNull(line[1].style)
    }

    @Test
    fun `parseInlineStyles handles no tags`() {
        val line = parseInlineStyles("plain text without any tags")
        assertEquals(1, line.size)
        assertEquals("plain text without any tags", line[0].text)
        assertNull(line[0].style)
    }

    @Test
    fun `parseInlineStyles handles malformed unclosed tag`() {
        // Unclosed <u> should apply style to everything until end of string
        val line = parseInlineStyles("<u>text")
        assertEquals(1, line.size)
        assertEquals("text", line[0].text)
        assertEquals(listOf("u"), line[0].style)
    }

    @Test
    fun `parseInlineStyles handles unmatched closing tag gracefully`() {
        // Unmatched </u> with no opener: tag is recognized and processed (style stack is empty, so nothing popped),
        // but the closing tag itself consumes no text before it, so the result is plain "text"
        val line = parseInlineStyles("text</u>")
        assertEquals(1, line.size)
        assertEquals("text", line[0].text)
        assertNull(line[0].style)
    }

    @Test
    fun `parseInlineStyles handles unknown tag gracefully`() {
        // Unknown <x> tag should be treated as raw text
        val line = parseInlineStyles("<x>text</x>")
        assertEquals(1, line.size)
        assertEquals("<x>text</x>", line[0].text)
        assertNull(line[0].style)
    }

    // ============================================================================================
    // convert() — verse kind mapping
    // ============================================================================================

    @Test
    fun `convert maps verse kinds correctly`() {
        val song = Song()
        song.code = "kinds"
        song.title = "Kinds Test"

        val normalVerse = Verse()
        normalVerse.kind = LegacyVerseKind.NORMAL
        normalVerse.lines = mutableListOf("normal line")

        val refrainVerse = Verse()
        refrainVerse.kind = LegacyVerseKind.REFRAIN
        refrainVerse.lines = mutableListOf("refrain line")

        val textVerse = Verse()
        textVerse.kind = LegacyVerseKind.TEXT
        textVerse.lines = mutableListOf("text line")

        val lyric = Lyric()
        lyric.verses = mutableListOf(normalVerse, refrainVerse, textVerse)

        song.lyrics = mutableListOf(lyric)

        val doc = convert(song)

        val lyricBlock = doc.blocks.filterIsInstance<LyricBlock>().first()
        assertEquals(VerseKind.NORMAL, lyricBlock.verses[0].kind)
        assertEquals(VerseKind.REFRAIN, lyricBlock.verses[1].kind)
        assertEquals(VerseKind.TEXT, lyricBlock.verses[2].kind)
    }

    // ============================================================================================
    // convert() → convertToLegacy() round-trip
    // ============================================================================================

    @Test
    fun `convertToLegacy is inverse of convert for round-trip fields`() {
        val original = Song()
        original.code = "roundtrip"
        original.title = "Round Trip Song"
        original.title_original = "Original Title"
        original.tune = "Simple Tune"
        original.authors_lyric = listOf("Lyricist One", "Lyricist Two")
        original.authors_music = listOf("Composer One")
        original.keySignature = "C"
        original.timeSignature = "4/4"
        original.scriptureReferences = "John.3.16"

        val verse = Verse()
        verse.kind = LegacyVerseKind.REFRAIN
        verse.lines = mutableListOf("<b>Bold line</b>", "Plain line", "<u>Underlined</u>")

        val lyric = Lyric()
        lyric.caption = "Stanza 1"
        lyric.verses = mutableListOf(verse)

        original.lyrics = mutableListOf(lyric)

        val doc = convert(original)
        val restored = convertToLegacy(doc)

        // Fields that round-trip through the document format
        assertEquals(original.code, restored.code)
        assertEquals(original.title, restored.title)
        assertEquals(original.title_original, restored.title_original)
        assertEquals(original.tune, restored.tune)
        assertEquals(original.keySignature, restored.keySignature)
        assertEquals(original.timeSignature, restored.timeSignature)
        assertEquals(original.scriptureReferences, restored.scriptureReferences)

        // Authors — order preserved within each list
        assertEquals(original.authors_lyric, restored.authors_lyric)
        assertEquals(original.authors_music, restored.authors_music)

        // Lyric structure
        assertEquals(1, restored.lyrics?.size ?: 0)
        val restoredLyric = restored.lyrics!![0]
        assertEquals(original.lyrics!![0].caption, restoredLyric.caption)
        assertEquals(1, restoredLyric.verses?.size ?: 0)

        val restoredVerse = restoredLyric.verses!![0]
        assertEquals(original.lyrics!![0].verses!![0].kind, restoredVerse.kind)

        // Lines — inline styles reconstructed as tags
        val origLines = original.lyrics!![0].verses!![0].lines!!
        val restLines = restoredVerse.lines!!
        assertEquals(origLines.size, restLines.size)
        assertEquals("<b>Bold line</b>", restLines[0])
        assertEquals("Plain line", restLines[1])
        assertEquals("<u>Underlined</u>", restLines[2])
    }

    // ============================================================================================
    // convertToLegacy() — authors from RowBlock
    // ============================================================================================

    @Test
    fun `convertToLegacy reconstructs authors from row block`() {
        val doc = SongDocument(
            code = "authors_test",
            meta = SongMeta(title = "Test Song", title_original = null),
            blocks = listOf(
                PBlock(role = "title", content = plainLine("Test Song")),
                RowBlock(
                    items = listOf(
                        PBlock(role = "authors_lyric", content = plainLine("First Lyricist; Second Lyricist")),
                        PBlock(role = "authors_music", content = plainLine("Composer Name"))
                    )
                )
            )
        )

        val song = convertToLegacy(doc)

        assertEquals(2, song.authors_lyric?.size)
        assertEquals("First Lyricist", song.authors_lyric!![0])
        assertEquals("Second Lyricist", song.authors_lyric!![1])
        assertEquals(1, song.authors_music?.size)
        assertEquals("Composer Name", song.authors_music!![0])
    }

    // ============================================================================================
    // convertToLegacy() — musical block splitting
    // ============================================================================================

    @Test
    fun `convertToLegacy splits musical block correctly`() {
        val doc = SongDocument(
            code = "musical_test",
            meta = SongMeta(title = "Musical Test", title_original = null),
            blocks = listOf(
                PBlock(role = "title", content = plainLine("Musical Test")),
                PBlock(role = "musical", content = plainLine("Bes 6/8"))
            )
        )

        val song = convertToLegacy(doc)

        assertEquals("Bes", song.keySignature)
        assertEquals("6/8", song.timeSignature)
    }

    @Test
    fun `convertToLegacy handles single-word musical`() {
        val doc = SongDocument(
            code = "single_musical",
            meta = SongMeta(title = "Single Musical", title_original = null),
            blocks = listOf(
                PBlock(role = "title", content = plainLine("Single Musical")),
                PBlock(role = "musical", content = plainLine("Do=C"))
            )
        )

        val song = convertToLegacy(doc)

        assertEquals("Do=C", song.keySignature)
        assertNull(song.timeSignature)
    }

    // ============================================================================================
    // convertToLegacy() — lyric block reconstruction
    // ============================================================================================

    @Test
    fun `convertToLegacy reconstructs lyric block with all verse kinds`() {
        val doc = SongDocument(
            code = "lyric_recon",
            meta = SongMeta(title = "Lyric Recon", title_original = null),
            blocks = listOf(
                PBlock(role = "title", content = plainLine("Lyric Recon")),
                LyricBlock(
                    caption = plainLine("Verse 1"),
                    verses = listOf(
                        DocVerse(
                            kind = VerseKind.NORMAL,
                            lines = listOf(
                                VerseLine(content = plainLine("Numbered verse line"))
                            )
                        ),
                        DocVerse(
                            kind = VerseKind.REFRAIN,
                            lines = listOf(
                                VerseLine(content = plainLine("Refrain line"))
                            )
                        ),
                        DocVerse(
                            kind = VerseKind.TEXT,
                            lines = listOf(
                                VerseLine(content = plainLine("Spoken line"))
                            )
                        )
                    )
                )
            )
        )

        val song = convertToLegacy(doc)

        assertEquals(1, song.lyrics?.size ?: 0)
        val lyric = song.lyrics!![0]
        assertEquals("Verse 1", lyric.caption)
        assertEquals(3, lyric.verses?.size ?: 0)
        assertEquals(LegacyVerseKind.NORMAL, lyric.verses!![0].kind)
        assertEquals(LegacyVerseKind.REFRAIN, lyric.verses!![1].kind)
        assertEquals(LegacyVerseKind.TEXT, lyric.verses!![2].kind)
    }

    // ============================================================================================
    // Helper utilities
    // ============================================================================================

    /**
     * Convert a [Line] (list of [Span]s) to a plain string by concatenating all span text.
     */
    private fun Line.joinText(): String = joinToString("") { it.text }

    /**
     * Create a plain (unstyled) [Line] from a single string.
     */
    private fun plainLine(text: String): Line = listOf(Span(text = text, style = null))
}