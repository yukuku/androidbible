package yuku.alkitab.base.storage

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.App
import yuku.alkitab.base.S
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34])
class BuiltInGalleryTest {
    private val source = File("../tools/in-ddd/in-ddd.yet").readLines().map { it.split('\t') }
    private val reader = InternalReader("ddd", "in", "DDD", "Dummy Debug Data", OldVerseTextDecoder.Utf8())
    private lateinit var originalDimensions: S.CalculatedDimensions

    @Before
    fun setUp() {
        App.initWithAppContext(ApplicationProvider.getApplicationContext())
        originalDimensions = S.applied()
        S.overrideAppliedDimensions(S.CalculatedDimensions().apply {
            fontColor = 0xff212121.toInt()
            fontRedColor = 0xffb71c1c.toInt()
            verseNumberColor = 0xff828282.toInt()
            indentParagraphRest = 5
        })
    }

    @After
    fun tearDown() {
        S.overrideAppliedDimensions(originalDimensions)
    }

    @Test
    fun `every packaged chapter matches the source including Unicode and empty verses`() {
        val chapters = source.filter { it[0] == "verse" }.groupBy { it[1].toInt() to it[2].toInt() }
        val books = reader.loadBooks()
        assertEquals(66, books.size)
        assertEquals(255, books[1].chapter_count)
        assertEquals(chapters.size, books.sumOf { it.chapter_count })
        for (book in books) {
            for (chapter in 1..book.chapter_count) {
                val expected = chapters.getValue(book.bookId + 1 to chapter)
                val actual = reader.loadVerseText(book, chapter, false, false)
                assertEquals(expected.size, actual.verseCount)
                expected.forEachIndexed { index, row ->
                    assertEquals("${book.bookId + 1}:$chapter:${index + 1}", row[4], actual.getVerse(index))
                }
            }
        }
    }

    @Test
    fun `gallery markers resolve to the packaged note and reference content`() {
        val entries = source.filter { it[0] == "footnote" || it[0] == "xref" }
            .associateBy { it.take(5) }
        val referenced = mutableSetOf<List<String>>()
        for (row in source.filter { it[0] == "verse" && it[1] == "1" && it[2].toInt() <= 6 }) {
            val ari = Ari.encode(0, row[2].toInt(), row[3].toInt())
            for (match in Regex("@<([fx])(\\d+)@>").findAll(row[4])) {
                val kind = if (match.groupValues[1] == "f") "footnote" else "xref"
                val key = listOf(kind) + row.subList(1, 4) + match.groupValues[2]
                val expected = entries.getValue(key)[5]
                val arif = (ari shl 8) or match.groupValues[2].toInt()
                val actual = if (kind == "footnote") reader.getFootnoteEntry(arif)?.content else reader.getXrefEntry(arif)?.content
                assertEquals(key.toString(), expected, actual)
                referenced.add(key)
            }
        }
        assertEquals(entries.keys.filter { it[1] == "1" && it[2].toInt() <= 6 }.toSet(), referenced)
    }

    @Test
    fun `gallery headings and parallel references survive conversion`() {
        for (chapter in 1..6) {
            val expected = source.filter { it[0] == "pericope" && it[1] == "1" && it[2] == chapter.toString() }
            val aris = IntArrayList()
            val blocks = mutableListOf<PericopeBlock>()
            reader.loadPericope(0, chapter, aris, blocks)
            assertEquals(expected.map { it[4] }, blocks.map { it.title })
            expected.forEachIndexed { index, row ->
                assertEquals(Ari.encode(0, chapter, row[3].toInt()), aris[index])
            }
            if (chapter == 4) {
                assertEquals(
                    listOf("@o:Gen.3.1 Ruby types", "@a:1281-1283 Ruby layout", "@o:Gen.6.1 Attribute wrapping"),
                    blocks.last().parallels.toList(),
                )
            }
        }
    }

    @Test
    fun `gallery destinations decode to existing verses`() {
        val addresses = source.filter { it[0] == "verse" }
            .map { Ari.encode(it[1].toInt() - 1, it[2].toInt(), it[3].toInt()) }.toSet()
        val targets = source.filter {
            it[0] in listOf("footnote", "xref") && it[1] == "1" && it[2].toInt() <= 6
        }.flatMap { row -> Regex("@<t([^@]+)@>").findAll(row[5]).map { it.groupValues[1] }.toList() } +
            source.filter { it[0] == "parallel" && it[1].startsWith('@') }
                .map { it[1].substringAfter('@').substringBefore(' ') }
        assertTrue(targets.isNotEmpty())
        for (target in targets) {
            val ranges = TargetDecoder.decode(target)
            assertTrue("Invalid target $target", ranges.size() > 0)
            for (index in 0 until ranges.size()) {
                assertTrue("Missing destination in $target", ranges[index] in addresses)
            }
        }
    }

    @Test
    fun `gallery ruby and link markup parses with verse numbers shown and hidden`() {
        val kinds = mutableSetOf<Char?>()
        for (row in source.filter { it[0] == "verse" && it[1] == "1" && it[2].toInt() <= 6 }) {
            val ari = Ari.encode(0, row[2].toInt(), row[3].toInt())
            val expectedReadings = Regex("@<r[a-z]?=([^@]+)@>").findAll(row[4]).map { it.groupValues[1] }.toList()
            val expectedLinks = Regex("@<[fx]\\d+@>").findAll(row[4]).count()
            for (isVerseNumberShown in listOf(true, false)) {
                val result = VerseRendererCompose.render(isVerseNumberShown, ari, row[4])
                assertFalse("Markup leaked at $ari", result.text.text.contains('@'))
                assertEquals("Readings at $ari", expectedReadings, result.rubies.map { it.ruby })
                assertEquals("Links at $ari", expectedLinks, result.inlineLinks.size)
                for (ruby in result.rubies) {
                    assertTrue(ruby.start in 0..ruby.end && ruby.end <= result.text.length)
                    kinds.add(ruby.kind)
                }
            }
        }
        assertEquals(setOf(null, 'f', 'p', 's', 'g', 'm', 't', 'l', 'z'), kinds)
    }
}
