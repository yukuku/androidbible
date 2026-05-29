package yuku.alkitab.songs.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongDocumentJsonTest {

    @Test
    fun `round trip preserves simple song`() {
        val doc = SongDocument(
            v = 1,
            code = "25",
            meta = SongMeta(title = "Malam Kudus", title_original = "Silent Night"),
            blocks = listOf(
                PBlock(role = "title", content = plainLine("Malam Kudus")),
                RowBlock(
                    items = listOf(
                        PBlock(role = "authors_lyric", content = plainLine("Joseph Mohr")),
                        PBlock(role = "authors_music", content = plainLine("Franz X. Gruber"))
                    )
                ),
                LyricBlock(
                    verses = listOf(
                        Verse(
                            kind = VerseKind.NORMAL,
                            lines = listOf(
                                VerseLine(content = plainLine("Malam Kudus, sunyi senyap,")),
                                VerseLine(
                                    content = listOf(
                                        Span(text = "Sia", style = listOf("u")),
                                        Span(text = "pa yang b'lum lelap;")
                                    )
                                ),
                                VerseLine(
                                    size = 1.3f,
                                    content = listOf(Span(text = "Big line"))
                                )
                            )
                        )
                    )
                ),
                ScriptureBlock(osis = "John.3.16; Rom.5.8"),
                YoutubeBlock(videoId = "abc123")
            )
        )

        val json = SongDocumentJson.encodeToString(doc)
        val decoded = SongDocumentJson.decodeFromString(json)

        assertEquals(doc.v, decoded.v)
        assertEquals(doc.code, decoded.code)
        assertEquals(doc.meta?.title, decoded.meta?.title)
        assertEquals(doc.meta?.title_original, decoded.meta?.title_original)
        assertEquals(doc.blocks.size, decoded.blocks.size)
    }

    @Test
    fun `round trip preserves styled lines`() {
        val doc = SongDocument(
            v = 1,
            code = "1",
            meta = null,
            blocks = listOf(
                LyricBlock(
                    verses = listOf(
                        Verse(
                            kind = VerseKind.NORMAL,
                            lines = listOf(
                                VerseLine(content = listOf(Span(text = "underline", style = listOf("u")))),
                                VerseLine(content = listOf(Span(text = "bold", style = listOf("b")))),
                                VerseLine(content = listOf(Span(text = "italic", style = listOf("i")))),
                                VerseLine(content = listOf(Span(text = "bold_italic", style = listOf("b", "i"))))
                            )
                        )
                    )
                )
            )
        )

        val json = SongDocumentJson.encodeToString(doc)
        val decoded = SongDocumentJson.decodeFromString(json)

        val lyricBlock = decoded.blocks[0] as LyricBlock
        val verses = lyricBlock.verses[0]
        assertEquals(4, verses.lines.size)
        assertEquals(listOf("u"), verses.lines[0].content[0].style)
        assertEquals(listOf("b"), verses.lines[1].content[0].style)
        assertEquals(listOf("i"), verses.lines[2].content[0].style)
        assertEquals(listOf("b", "i"), verses.lines[3].content[0].style)
    }

    @Test
    fun `round trip preserves verse line with size and align`() {
        val doc = SongDocument(
            v = 1,
            code = "1",
            meta = null,
            blocks = listOf(
                LyricBlock(
                    verses = listOf(
                        Verse(
                            kind = VerseKind.NORMAL,
                            lines = listOf(
                                VerseLine(size = 1.3f, align = "center", content = plainLine("Centered big text"))
                            )
                        )
                    )
                )
            )
        )

        val json = SongDocumentJson.encodeToString(doc)
        val decoded = SongDocumentJson.decodeFromString(json)

        val lyricBlock = decoded.blocks[0] as LyricBlock
        val verseLine = lyricBlock.verses[0].lines[0]
        assertEquals(1.3f, verseLine.size!!, 0.001f)
        assertEquals("center", verseLine.align)
        assertEquals("Centered big text", verseLine.content[0].text)
    }

    @Test
    fun `round trip preserves all block types`() {
        val doc = SongDocument(
            v = 1,
            code = "1",
            meta = null,
            blocks = listOf(
                PBlock(role = "title", content = plainLine("Title")),
                RowBlock(
                    items = listOf(
                        PBlock(content = plainLine("Left")),
                        PBlock(content = plainLine("Right"))
                    )
                ),
                LyricBlock(
                    verses = listOf(
                        Verse(kind = VerseKind.NORMAL, lines = listOf(VerseLine(content = plainLine("Lyric"))))
                    )
                ),
                ScriptureBlock(osis = "John.3.16"),
                YoutubeBlock(videoId = "abc123")
            )
        )

        val json = SongDocumentJson.encodeToString(doc)
        val decoded = SongDocumentJson.decodeFromString(json)

        assertEquals(5, decoded.blocks.size)
        assertTrue(decoded.blocks[0] is PBlock)
        assertTrue(decoded.blocks[1] is RowBlock)
        assertTrue(decoded.blocks[2] is LyricBlock)
        assertTrue(decoded.blocks[3] is ScriptureBlock)
        assertTrue(decoded.blocks[4] is YoutubeBlock)
    }

    @Test
    fun `round trip preserves song book document`() {
        val book = SongBookDocument(
            v = 1,
            book = SongBookMeta(name = "kidung", title = "Kidung Jemaat", copyright = "© 2024"),
            songs = listOf(
                SongDocument(
                    v = 1,
                    code = "1",
                    meta = SongMeta(title = "Song 1", title_original = null),
                    blocks = listOf(PBlock(content = plainLine("Hello")))
                )
            )
        )

        val json = SongDocumentJson.encodeToString(book)
        val decoded = SongDocumentJson.decodeFromStringSongBook(json)

        assertEquals(book.v, decoded.v)
        assertEquals(book.book.name, decoded.book.name)
        assertEquals(book.songs.size, decoded.songs.size)
    }

    @Test
    fun `unknown block type is preserved in round trip`() {
        val json = """
            {
                "v": 1,
                "code": "1",
                "meta": null,
                "blocks": [
                    {"type": "p", "role": "title", "content": "Title"},
                    {"type": "future_block", "content": "Future content"}
                ]
            }
        """.trimIndent()
        val doc = SongDocumentJson.decodeFromString(json)
        assertEquals(2, doc.blocks.size)
        val unknown = doc.blocks[1] as UnknownBlock
        assertEquals("future_block", unknown.type)
    }

    @Test
    fun `unknown block type is preserved alongside known blocks`() {
        val json = """
            {
                "v": 1,
                "code": "1",
                "meta": null,
                "blocks": [
                    {"type": "p", "content": "Known block"},
                    {"type": "future_block", "data": "unknown"},
                    {"type": "youtube", "videoId": "xyz"}
                ]
            }
        """.trimIndent()

        val doc = SongDocumentJson.decodeFromString(json)
        assertEquals(3, doc.blocks.size)
        assertTrue(doc.blocks[0] is PBlock)
        assertTrue(doc.blocks[1] is UnknownBlock)
        assertTrue(doc.blocks[2] is YoutubeBlock)
    }

    @Test
    fun `plain text line serializes as string`() {
        val doc = SongDocument(
            v = 1,
            code = "1",
            meta = null,
            blocks = listOf(
                PBlock(content = plainLine("Hello World"))
            )
        )

        val json = SongDocumentJson.encodeToString(doc)
        assertTrue("Plain line should serialize as string", json.contains("\"Hello World\""))
    }

    @Test
    fun `styled line serializes as array`() {
        val doc = SongDocument(
            v = 1,
            code = "1",
            meta = null,
            blocks = listOf(
                PBlock(content = listOf(Span(text = "Hello", style = listOf("u"))))
            )
        )

        val json = SongDocumentJson.encodeToString(doc)
        assertTrue("Styled line should serialize as array", json.contains("[{\"text\":\"Hello\",\"style\":[\"u\"]}]"))
    }

    @Test
    fun `verse kind serializes as lowercase string`() {
        val doc = SongDocument(
            v = 1,
            code = "1",
            meta = null,
            blocks = listOf(
                LyricBlock(
                    verses = listOf(
                        Verse(kind = VerseKind.REFRAIN, lines = emptyList())
                    )
                )
            )
        )

        val json = SongDocumentJson.encodeToString(doc)
        assertTrue("VerseKind should serialize as lowercase", json.contains("\"kind\":\"refrain\""))
    }

    @Test
    fun `meta is preserved in round trip`() {
        val doc = SongDocument(
            v = 2,
            code = "42",
            meta = SongMeta(title = "Great is Thy Faithfulness", title_original = "Great Is Thy Faithfulness"),
            blocks = listOf(PBlock(content = plainLine(" lyrics")))
        )

        val json = SongDocumentJson.encodeToString(doc)
        val decoded = SongDocumentJson.decodeFromString(json)

        assertEquals(doc.v, decoded.v)
        assertEquals(doc.code, decoded.code)
        assertEquals(doc.meta?.title, decoded.meta?.title)
        assertEquals(doc.meta?.title_original, decoded.meta?.title_original)
    }
}
