package yuku.alkitab.yes2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import yuku.alkitab.yes2.compress.SnappyOutputStream
import yuku.alkitab.yes2.io.RandomAccessFileRandomInputStream
import yuku.alkitab.yes2.io.RandomAccessFileRandomOutputStream
import yuku.alkitab.yes2.io.RandomOutputStream
import yuku.alkitab.yes2.model.PericopeData
import yuku.alkitab.yes2.model.VerseBytes
import yuku.alkitab.yes2.model.Yes2Book
import yuku.alkitab.yes2.section.BooksInfoSection
import yuku.alkitab.yes2.section.PericopesSection
import yuku.alkitab.yes2.section.VersionInfoSection
import yuku.alkitab.yes2.section.base.SectionContent
import yuku.bintex.BintexWriter
import yuku.bintex.ValueMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

class Yes2RoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    data class TestBook(
        val bookId: Int,
        val shortName: String,
        val abbreviation: String? = null,
        val chapters: List<List<String>>,
    )

    private fun writeYes(
        books: List<TestBook>,
        versionShortName: String = "TST",
        versionLongName: String = "Test Bible",
        versionDescription: String = "A test Bible",
        locale: String = "en",
        compressed: Boolean = false,
        pericopeData: PericopeData? = null,
    ): File {
        val file = tmp.newFile("test-${System.nanoTime()}.yes")

        val versionInfo = VersionInfoSection().apply {
            shortName = versionShortName
            longName = versionLongName
            description = versionDescription
            this.locale = locale
            book_count = books.size
            hasPericopes = if (pericopeData != null) 1 else 0
            textEncoding = 2 // utf-8
        }

        val booksInfo = BooksInfoSection().apply {
            yes2Books = buildYes2Books(books)
        }

        val textSection = InlineTextSection(books, compressed)

        val writer = Yes2Writer()
        writer.sections.add(versionInfo)
        writer.sections.add(booksInfo)
        if (pericopeData != null) {
            writer.sections.add(PericopesSection(pericopeData))
        }
        writer.sections.add(textSection)

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val out: RandomOutputStream = RandomAccessFileRandomOutputStream(raf)
            writer.writeToFile(out)
        }

        return file
    }

    private fun buildYes2Books(books: List<TestBook>): List<Yes2Book> {
        val result = ArrayList<Yes2Book>(books.size)
        var offsetTotal = 0
        for (book in books) {
            val y = Yes2Book().apply {
                bookId = book.bookId
                shortName = book.shortName
                abbreviation = book.abbreviation
                chapter_count = book.chapters.size
                verse_counts = IntArray(book.chapters.size) { book.chapters[it].size }
                chapter_offsets = IntArray(book.chapters.size + 1)
                offset = offsetTotal
            }
            var offsetPassed = 0
            y.chapter_offsets[0] = 0
            for ((chapter0, verses) in book.chapters.withIndex()) {
                for (text in verses) {
                    offsetPassed += VerseBytes.bytesForAVerse(text).size
                }
                y.chapter_offsets[chapter0 + 1] = offsetPassed
            }
            result.add(y)
            offsetTotal += offsetPassed
        }
        return result
    }

    private class InlineTextSection(
        private val books: List<TestBook>,
        private val compressed: Boolean,
    ) : SectionContent("text"), SectionContent.Writer {
        private val outputBuffer = ByteArrayOutputStream()
        private val snappy: SnappyOutputStream?
        private val attributes: ValueMap?

        init {
            val sink = if (compressed) {
                SnappyOutputStream(outputBuffer, BLOCK_SIZE).also { snappy = it }
            } else {
                snappy = null
                outputBuffer
            }
            val bw = BintexWriter(sink)
            for (book in books) {
                for (verses in book.chapters) {
                    for (text in verses) {
                        bw.writeRaw(VerseBytes.bytesForAVerse(text))
                    }
                }
            }
            if (snappy != null) {
                snappy.flush()
                val compressedBlockSizes = snappy.compressedBlockSizes
                val compressionInfo = ValueMap().apply {
                    put("block_size", BLOCK_SIZE)
                    put("compressed_block_sizes", compressedBlockSizes)
                }
                attributes = ValueMap().apply {
                    put("compression.name", "snappy-blocks")
                    put("compression.version", 1)
                    put("compression.info", compressionInfo)
                }
            } else {
                attributes = null
            }
        }

        override fun getAttributes(): ValueMap? = attributes

        override fun write(output: RandomOutputStream) {
            outputBuffer.writeTo(output)
        }

        companion object {
            const val BLOCK_SIZE = 32768
        }
    }

    private fun openReader(file: File): Yes2Reader {
        return Yes2Reader(RandomAccessFileRandomInputStream(file.absolutePath))
    }

    @Test
    fun `writes a single-book Bible uncompressed and reads every verse back exactly`() {
        val book = TestBook(
            bookId = 0,
            shortName = "Genesis",
            abbreviation = "Gen",
            chapters = listOf(
                listOf("In the beginning", "And the earth was without form", "And God said let there be light"),
                listOf("Thus the heavens were finished", "And on the seventh day"),
            ),
        )

        val file = writeYes(books = listOf(book))
        val reader = openReader(file)

        assertEquals("en", reader.locale)
        assertEquals("TST", reader.shortName)
        assertEquals("Test Bible", reader.longName)
        assertEquals("A test Bible", reader.description)

        val loadedBooks = reader.loadBooks()!!
        assertEquals(1, loadedBooks.size)
        val loaded = loadedBooks[0] as Yes2Book
        assertEquals(0, loaded.bookId)
        assertEquals("Genesis", loaded.shortName)
        assertEquals("Gen", loaded.abbreviation)
        assertEquals(2, loaded.chapter_count)
        assertArrayEquals(intArrayOf(3, 2), loaded.verse_counts)

        val chapter1 = reader.loadVerseText(loaded, 1, false, false)!!
        assertEquals(3, chapter1.verseCount)
        assertEquals("In the beginning", chapter1.getVerse(0))
        assertEquals("And the earth was without form", chapter1.getVerse(1))
        assertEquals("And God said let there be light", chapter1.getVerse(2))

        val chapter2 = reader.loadVerseText(loaded, 2, false, false)!!
        assertEquals(2, chapter2.verseCount)
        assertEquals("Thus the heavens were finished", chapter2.getVerse(0))
        assertEquals("And on the seventh day", chapter2.getVerse(1))
    }

    @Test
    fun `round-trips a multi-book Bible preserving book ordering and chapter offsets`() {
        val books = listOf(
            TestBook(
                bookId = 0,
                shortName = "Genesis",
                chapters = listOf(
                    listOf("Gen 1:1", "Gen 1:2"),
                    listOf("Gen 2:1"),
                ),
            ),
            TestBook(
                bookId = 1,
                shortName = "Exodus",
                abbreviation = "Exo",
                chapters = listOf(
                    listOf("Exo 1:1", "Exo 1:2", "Exo 1:3", "Exo 1:4"),
                ),
            ),
            TestBook(
                bookId = 65,
                shortName = "Revelation",
                chapters = listOf(
                    listOf("Rev 1:1"),
                ),
            ),
        )

        val file = writeYes(books = books)
        val reader = openReader(file)
        val loadedBooks = reader.loadBooks()!!
        assertEquals(3, loadedBooks.size)

        val gen = loadedBooks[0] as Yes2Book
        val exo = loadedBooks[1] as Yes2Book
        val rev = loadedBooks[2] as Yes2Book

        assertEquals(0, gen.bookId)
        assertEquals(1, exo.bookId)
        assertEquals(65, rev.bookId)
        assertEquals("Exo", exo.abbreviation)
        assertNull(gen.abbreviation)

        // Boundaries between books: read a verse from each book and confirm separation.
        assertEquals("Gen 1:1", reader.loadVerseText(gen, 1, false, false)!!.getVerse(0))
        assertEquals("Gen 2:1", reader.loadVerseText(gen, 2, false, false)!!.getVerse(0))
        assertEquals(
            listOf("Exo 1:1", "Exo 1:2", "Exo 1:3", "Exo 1:4"),
            (0 until 4).map { reader.loadVerseText(exo, 1, false, false)!!.getVerse(it) },
        )
        assertEquals("Rev 1:1", reader.loadVerseText(rev, 1, false, false)!!.getVerse(0))
    }

    @Test
    fun `round-trips UTF-8 text without corruption for BMP code points`() {
        // Yes2VerseTextDecoder uses Utf8Decoder which is documented as "intentionally incomplete"
        // and only supports code points up to U+FFFF (no 4-byte UTF-8 sequences / supplementary plane),
        // so the verses here stay inside the BMP.
        val book = TestBook(
            bookId = 0,
            shortName = "Kejadian",
            chapters = listOf(
                listOf(
                    "Pada mulanya Allah menciptakan langit dan bumi.",
                    "Ἐν ἀρχῇ ἦν ὁ Λόγος",
                    "בְּרֵאשִׁית בָּרָא אֱלֹהִים",
                    "café naïve résumé €",
                ),
            ),
        )

        val file = writeYes(books = listOf(book), locale = "id")
        val reader = openReader(file)
        val loaded = reader.loadBooks()!![0]
        val chapter1 = reader.loadVerseText(loaded, 1, false, false)!!
        assertEquals(4, chapter1.verseCount)
        assertEquals("Pada mulanya Allah menciptakan langit dan bumi.", chapter1.getVerse(0))
        assertEquals("Ἐν ἀρχῇ ἦν ὁ Λόγος", chapter1.getVerse(1))
        assertEquals("בְּרֵאשִׁית בָּרָא אֱלֹהִים", chapter1.getVerse(2))
        assertEquals("café naïve résumé €", chapter1.getVerse(3))
        assertEquals("id", reader.locale)
    }

    @Test
    fun `loadVerseText with dontSeparateVerses joins every verse into one newline-delimited string`() {
        val book = TestBook(
            bookId = 0,
            shortName = "Gen",
            chapters = listOf(listOf("alpha", "beta", "gamma")),
        )

        val file = writeYes(books = listOf(book))
        val reader = openReader(file)
        val loaded = reader.loadBooks()!![0]

        val joined = reader.loadVerseText(loaded, 1, true, false)!!
        assertEquals(1, joined.verseCount)
        assertEquals("alpha\nbeta\ngamma\n", joined.getVerse(0))
    }

    @Test
    fun `loadVerseText with lowercase flag lowercases UTF-8 text in-place`() {
        val book = TestBook(
            bookId = 0,
            shortName = "Gen",
            chapters = listOf(listOf("MixedCase TEXT", "Another Case")),
        )

        val file = writeYes(books = listOf(book))
        val reader = openReader(file)
        val loaded = reader.loadBooks()!![0]

        val verses = reader.loadVerseText(loaded, 1, false, true)!!
        assertEquals(2, verses.verseCount)
        assertEquals("mixedcase text", verses.getVerse(0))
        assertEquals("another case", verses.getVerse(1))
    }

    @Test
    fun `loadVerseText returns null for out-of-range chapter`() {
        val book = TestBook(
            bookId = 0,
            shortName = "Gen",
            chapters = listOf(listOf("only verse")),
        )

        val file = writeYes(books = listOf(book))
        val reader = openReader(file)
        val loaded = reader.loadBooks()!![0]

        assertNull(reader.loadVerseText(loaded, 0, false, false))
        assertNull(reader.loadVerseText(loaded, 2, false, false))
    }

    @Test
    fun `round-trips pericopes and returns them by book-chapter`() {
        val book = TestBook(
            bookId = 0,
            shortName = "Gen",
            chapters = listOf(
                listOf("v1", "v2", "v3"),
                listOf("v1", "v2"),
            ),
        )

        val pericopeData = PericopeData().apply {
            addEntry(
                PericopeData.Entry().apply {
                    ari = Ari.encode(0, 1, 1)
                    block = PericopeData.Block().apply {
                        title = "Creation"
                        addParallel("John 1:1")
                    }
                }
            )
            addEntry(
                PericopeData.Entry().apply {
                    ari = Ari.encode(0, 2, 1)
                    block = PericopeData.Block().apply { title = "Seventh Day" }
                }
            )
        }

        val file = writeYes(books = listOf(book), pericopeData = pericopeData)
        val reader = openReader(file)

        val aris = IntArrayList()
        val blocks = ArrayList<PericopeBlock>()
        val count = reader.loadPericope(0, 1, aris, blocks)
        assertEquals(1, count)
        assertEquals(1, aris.size())
        assertEquals(Ari.encode(0, 1, 1), aris[0])
        assertEquals("Creation", blocks[0].title)
        assertArrayEquals(arrayOf("John 1:1"), blocks[0].parallels)

        val aris2 = IntArrayList()
        val blocks2 = ArrayList<PericopeBlock>()
        val count2 = reader.loadPericope(0, 2, aris2, blocks2)
        assertEquals(1, count2)
        assertEquals(Ari.encode(0, 2, 1), aris2[0])
        assertEquals("Seventh Day", blocks2[0].title)
        assertEquals(0, blocks2[0].parallels.size)
    }

    @Test
    fun `loadPericope returns zero when the chapter has no pericopes`() {
        val book = TestBook(
            bookId = 0,
            shortName = "Gen",
            chapters = listOf(listOf("v1"), listOf("v1")),
        )

        val pericopeData = PericopeData().apply {
            addEntry(
                PericopeData.Entry().apply {
                    ari = Ari.encode(0, 1, 1)
                    block = PericopeData.Block().apply { title = "Only in ch1" }
                }
            )
        }

        val file = writeYes(books = listOf(book), pericopeData = pericopeData)
        val reader = openReader(file)

        val aris = IntArrayList()
        val blocks = ArrayList<PericopeBlock>()
        assertEquals(0, reader.loadPericope(0, 2, aris, blocks))
        assertEquals(0, aris.size())
        assertEquals(0, blocks.size)
    }

    @Test
    fun `loadPericope returns zero when the file has no pericope section`() {
        val book = TestBook(
            bookId = 0,
            shortName = "Gen",
            chapters = listOf(listOf("v1")),
        )

        val file = writeYes(books = listOf(book), pericopeData = null)
        val reader = openReader(file)

        val aris = IntArrayList()
        val blocks = ArrayList<PericopeBlock>()
        assertEquals(0, reader.loadPericope(0, 1, aris, blocks))
    }

    @Test
    fun `snappy-compressed text section reads back identical verses`() {
        // Use enough text to force multiple Snappy blocks (block size is 32 KB in the writer).
        val longVerse = "The quick brown fox jumps over the lazy dog. ".repeat(200)
        val book = TestBook(
            bookId = 0,
            shortName = "Gen",
            chapters = listOf(
                (1..50).map { "chapter-1 verse-$it: $longVerse" },
                (1..30).map { "chapter-2 verse-$it: $longVerse" },
            ),
        )

        val file = writeYes(books = listOf(book), compressed = true)
        val reader = openReader(file)
        val loaded = reader.loadBooks()!![0]

        val chapter1 = reader.loadVerseText(loaded, 1, false, false)!!
        assertEquals(50, chapter1.verseCount)
        for (i in 0 until 50) {
            assertEquals("chapter-1 verse-${i + 1}: $longVerse", chapter1.getVerse(i))
        }

        val chapter2 = reader.loadVerseText(loaded, 2, false, false)!!
        assertEquals(30, chapter2.verseCount)
        for (i in 0 until 30) {
            assertEquals("chapter-2 verse-${i + 1}: $longVerse", chapter2.getVerse(i))
        }
    }

    @Test
    fun `a compressed file is strictly smaller than its uncompressed equivalent for highly repetitive text`() {
        val repeated = "A".repeat(5000)
        val book = TestBook(
            bookId = 0,
            shortName = "Gen",
            chapters = listOf((1..20).map { repeated }),
        )

        val uncompressed = writeYes(books = listOf(book), compressed = false)
        val compressed = writeYes(books = listOf(book), compressed = true)

        assertTrue(
            "expected compressed size ${compressed.length()} < uncompressed ${uncompressed.length()}",
            compressed.length() < uncompressed.length(),
        )

        // Sanity: both files decode identically. Each Yes2Reader holds state (sectionIndex,
        // versionInfo_, textSectionReader_) that is lazily populated; reusing a single reader
        // per file avoids an NPE in loadVerseText when versionInfo_ has not yet been loaded.
        val plainReader = openReader(uncompressed)
        val snappyReader = openReader(compressed)
        val plain = plainReader.loadVerseText(plainReader.loadBooks()!![0], 1, false, false)!!
        val snappy = snappyReader.loadVerseText(snappyReader.loadBooks()!![0], 1, false, false)!!
        assertEquals(plain.verseCount, snappy.verseCount)
        for (i in 0 until plain.verseCount) {
            assertEquals(plain.getVerse(i), snappy.getVerse(i))
        }
    }

    @Test
    fun `getXrefEntry and getFootnoteEntry return null when the sections are not present`() {
        val book = TestBook(
            bookId = 0,
            shortName = "Gen",
            chapters = listOf(listOf("v1")),
        )
        val file = writeYes(books = listOf(book))
        val reader = openReader(file)
        // Trigger header/section-index load and then request absent sections.
        assertNotNull(reader.loadBooks())
        assertNull(reader.getXrefEntry(Ari.encode(0, 1, 1) shl 8 or 1))
        assertNull(reader.getFootnoteEntry(Ari.encode(0, 1, 1) shl 8 or 1))
    }
}
