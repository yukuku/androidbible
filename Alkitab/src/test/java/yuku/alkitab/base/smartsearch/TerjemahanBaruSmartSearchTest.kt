package yuku.alkitab.base.smartsearch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.base.util.SearchEngineQuery
import yuku.alkitab.model.Version
import yuku.alkitab.yes2.Yes2Reader
import yuku.alkitab.yes2.io.RandomAccessFileRandomInputStream
import yuku.alkitab.yes2.section.LexiconSection

/**
 * Smart search against the real Terjemahan Baru text and the word list its `.yet` carries. The text
 * is licensed and not in this repository, so the test is skipped unless it is given:
 *
 * ```
 * ALKITAB_TB_YET=/path/to/in-tb.yet \
 * ALKITAB_TB_YES=/path/to/in-tb.yes \
 * ALKITAB_PROPRIETARY_DIR=/path/to/androidbible-proprietary \
 * ./gradlew testPlainDebugUnitTest --tests '*TerjemahanBaruSmartSearchTest*' -i
 * ```
 *
 * `ALKITAB_TB_YES` and `ALKITAB_PROPRIETARY_DIR` are optional; with them, the yes file's lexicon
 * section and the internal `tb_lexicon_bt.bt` are checked against the `.yet`.
 *
 * The expected numbers are verse counts measured on that text; a divergence means the app and the
 * lexicon builder disagree about what a word is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerjemahanBaruSmartSearchTest {
    companion object {
        private var version: Version? = null
        private var families: Map<String, List<String>>? = null
        private var lexicon: SearchLexicon? = null
        private var vocabulary: VersionVocabulary? = null

        @BeforeClass
        @JvmStatic
        fun load() {
            val yet = System.getenv("ALKITAB_TB_YET")?.let(::File)?.takeIf { it.isFile } ?: return

            val verses = LinkedHashMap<Pair<Int, Int>, MutableList<String>>()
            val fams = LinkedHashMap<String, List<String>>()
            yet.forEachLine { line ->
                val f = line.split('\t')
                when (f[0]) {
                    "verse" -> verses.getOrPut(f[1].toInt() - 1 to f[2].toInt()) { mutableListOf() } += line.split('\t', limit = 5)[4]
                    "lexicon" -> fams[f[1]] = f.drop(2)
                }
            }
            if (fams.isEmpty()) return
            families = fams

            val v = versionOf(verses, locale = "in", shortName = "TB", lexicon = fams)
            version = v
            lexicon = LexiconRepository.builtInFor(v, "tb-test")
            vocabulary = VersionVocabulary.build("tb", v)
        }
    }

    private class Counts(val letters: Int, val smart: Int, val gained: Int, val dropped: Int)

    private fun counts(query: String, lex: SearchLexicon? = lexicon): Counts {
        val v = version!!
        val books = allBooksOf(v)
        val plan = SmartSearchPlanner(lex, vocabulary).plan(query)
        val smart = SearchEngine.searchByPlan(v, plan, books).result.toList().toSet()
        val letters = SearchEngine.searchByGrep(v, SearchEngineQuery(query, books)).toList().toSet()
        val c = Counts(letters.size, smart.size, (smart - letters).size, (letters - smart).size)
        println("%-18s letters %5d  smart %5d  +%-4d -%d".format(query, c.letters, c.smart, c.gained, c.dropped))
        return c
    }

    private fun requireData() = assumeTrue("ALKITAB_TB_YET is not set or its file carries no lexicon", version != null && lexicon != null)

    @Test
    fun `the yes file carries the same word families as the yet`() {
        requireData()
        val yes = System.getenv("ALKITAB_TB_YES")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("ALKITAB_TB_YES not set", yes != null)
        val reader = Yes2Reader(RandomAccessFileRandomInputStream(yes!!.absolutePath))
        assertEquals(families, reader.loadLexicon())
    }

    @Test
    fun `the internal lexicon file carries the same word families as the yet`() {
        requireData()
        val file = System.getenv("ALKITAB_PROPRIETARY_DIR")?.let { File(it, "overlay/yuku.alkitab/text_raw/tb_lexicon_bt.bt") }?.takeIf { it.isFile }
        assumeTrue("\$ALKITAB_PROPRIETARY_DIR/overlay/yuku.alkitab/text_raw/tb_lexicon_bt.bt missing", file != null)
        assertEquals(families, file!!.inputStream().use { LexiconSection.readFrom(it).families })
    }

    @Test
    fun `berkat stops returning berkata`() {
        requireData()
        val c = counts("berkat")
        assertEquals(3059, c.letters)
        assertTrue("smart ${c.smart}", c.smart in 260..300)
        assertEquals(0, c.gained)
    }

    @Test
    fun `kasih reaches the verses whose k the prefix swallowed`() {
        requireData()
        val c = counts("kasih")
        assertEquals(853, c.letters)
        assertTrue("gained ${c.gained}", c.gained >= 100)
    }

    @Test
    fun `iman and alam stop matching bagaimana and dalam`() {
        requireData()
        val iman = counts("iman")
        assertTrue("iman ${iman.smart}", iman.smart * 2 < iman.letters)
        val alam = counts("alam")
        assertTrue("alam ${alam.smart}", alam.smart * 10 < alam.letters)
    }

    @Test
    fun `sembuhkan sakit finds the healing stories and not only the command`() {
        requireData()
        val c = counts("sembuhkan sakit")
        assertEquals(6, c.letters)
        assertEquals(48, c.smart)
        assertEquals(0, c.dropped)
    }

    @Test
    fun `a derived form finds the whole family`() {
        requireData()
        assertEquals(225, counts("beriman").smart)
        assertEquals(828, counts("mengasihi").smart)
        assertEquals(944, counts("menyembah").smart)
        assertEquals(137, counts("mengampuni").smart)
    }

    @Test
    fun `explicit syntax is left alone`() {
        requireData()
        val exact = counts("+kasih")
        assertEquals(exact.letters, exact.smart)
        val phrase = counts("\"kota daud\"")
        assertEquals(phrase.letters, phrase.smart)
    }

    @Test
    fun `the rules-only lexicon is printed for comparison`() {
        requireData()
        val rules = RulesLexiconBuilder.build(vocabulary!!)
        println("rules-only lexicon: ${rules.families.size} families, ${rules.formCount} forms, built in ${rules.loadMillis} ms")
        for (q in listOf("kasih", "berkat", "pada", "iman", "sembuhkan sakit", "beriman")) counts(q, rules)
    }
}
