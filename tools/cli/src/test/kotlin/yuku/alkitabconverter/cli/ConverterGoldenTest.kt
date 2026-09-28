package yuku.alkitabconverter.cli

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.yes2.Yes2Reader
import yuku.alkitab.yes2.io.RandomAccessFileRandomInputStream
import yuku.alkitabconverter.yet.YetFileInput

/**
 * Runs the converters over the inputs checked into the repository and compares the results with
 * the outputs checked in next to them, so a change to the shared format code that silently alters
 * what the converters write fails here.
 */
class ConverterGoldenTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val repoRoot = File(System.getProperty("repoRoot"))
    private val placeholderYet = File(repoRoot, "tools/in-ddd/in-ddd.yet")

    @Test
    fun `yet2internal reproduces the placeholder Bible assets shipped with the plain flavor`() {
        val expectedDir = File(repoRoot, "Alkitab/src/plain/assets/internal")
        val outDir = tmp.newFolder("internal")

        assertEquals(0, runCli(arrayOf("yet2internal", placeholderYet.path, outDir.path)))

        val expected = expectedDir.list()!!.sorted()
        assertEquals(expected, outDir.list()!!.sorted())
        for (name in expected) {
            assertArrayEquals("content of $name", File(expectedDir, name).readBytes(), File(outDir, name).readBytes())
        }
    }

    @Test
    fun `yet2yes writes a compressed yes file that Yes2Reader reads back verse for verse`() {
        assertYesRoundTrip(compressed = true)
    }

    @Test
    fun `yet2yes writes an uncompressed yes file that Yes2Reader reads back verse for verse`() {
        assertYesRoundTrip(compressed = false)
    }

    private fun assertYesRoundTrip(compressed: Boolean) {
        val yesFile = File(tmp.root, "ddd.yes")
        val args = listOfNotNull("yet2yes", "--no-compress".takeUnless { compressed }, placeholderYet.path, yesFile.path)
        assertEquals(0, runCli(args.toTypedArray()))

        val yet = YetFileInput().parse(placeholderYet.path)
        val reader = Yes2Reader(RandomAccessFileRandomInputStream(yesFile.path))
        assertEquals(yet.infos["longName"], reader.longName)
        assertEquals(yet.infos["shortName"], reader.shortName)
        assertEquals(yet.infos["locale"], reader.locale)

        val books = checkNotNull(reader.loadBooks()).associateBy { it.bookId }
        assertEquals(yet.recs.map { it.book_1 - 1 }.toSet(), books.keys)

        for ((key, recs) in yet.recs.groupBy { it.book_1 to it.chapter_1 }) {
            val (book_1, chapter_1) = key
            val book = books.getValue(book_1 - 1)
            assertEquals(yet.bookNames[book_1], book.shortName)

            val verses: SingleChapterVerses = checkNotNull(reader.loadVerseText(book, chapter_1, false, false)) { "verses of $book_1:$chapter_1" }
            assertEquals("verse count of $book_1:$chapter_1", recs.size, verses.verseCount)
            for (rec in recs) {
                assertEquals("text of ${rec.book_1}:${rec.chapter_1}:${rec.verse_1}", rec.text, verses.getVerse(rec.verse_1 - 1))
            }
        }
    }

    @Test
    fun `rpa2rpb reproduces every checked-in reading plan built from an rpa of the same name`() {
        val plansDir = File(repoRoot, "tools/reading-plans")
        val pairs = plansDir.listFiles { f -> f.extension == "rpa" }!!
            .map { it to File(plansDir, it.nameWithoutExtension + ".rpb") }
            .filter { (_, rpb) -> rpb.exists() }
        assertTrue("found no rpa/rpb pairs in $plansDir", pairs.isNotEmpty())

        for ((rpa, expected) in pairs) {
            val out = File(tmp.root, expected.name)
            assertEquals(0, runCli(arrayOf("rpa2rpb", rpa.path, out.path)))
            assertArrayEquals("content of ${expected.name}", expected.readBytes(), out.readBytes())
        }
    }

    @Test
    fun `rpbdump reads back the plan info and day count of a converted reading plan`() {
        val rpb = File(repoRoot, "tools/reading-plans/bibleplan_gospels.rpb")

        val stdout = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(stdout, true, Charsets.UTF_8))
        val exitCode = try {
            runCli(arrayOf("rpbdump", rpb.path))
        } finally {
            System.setOut(original)
        }

        assertEquals(0, exitCode)
        val lines = stdout.toString(Charsets.UTF_8).lines()
        assertTrue(lines.contains("title: Bible Plan Gospels"))
        assertTrue(lines.contains("days: 30"))
    }
}
