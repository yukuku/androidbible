package yuku.alkitab.base.smartsearch

import android.text.Spanned
import android.text.style.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.base.util.SearchEngineQuery
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

/**
 * Smart search end to end on a small invented text: planning, family matching, highlighting and
 * the comparison with the letter search. Runs under Robolectric for SparseBooleanArray and spans.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartSearchEngineTest {
    private val version = versionOf(
        mapOf(
            (0 to 1) to listOf(
                "Aku mengasihi engkau, kata-Nya.",
                "Mereka berkata kepada raja.",
                "Engkau diberkati dan menjadi berkat.",
                "Tuhan menyembuhkan penyakitmu.",
                "Orang sakit itu sembuh.",
                "Belas kasihan-Nya besar.",
            ),
            (0 to 2) to listOf(
                "@@@^Kasih itu sabar.",
                "Iman dan bagaimana.",
                "@6Berkatilah@5 mereka yang @9mengutuk@7 kamu.",
            ),
        )
    )

    private val lexicon = lexiconOf(
        "kasih kasih mengasihi dikasihi",
        "berkat berkat diberkati berkatilah",
        "kata berkata kata kata-nya",
        "sembuh sembuh menyembuhkan",
        "sakit sakit penyakitmu",
        "iman iman beriman",
    )

    private val vocabulary = VersionVocabulary.build("test", version)
    private val planner = SmartSearchPlanner(lexicon, vocabulary)

    private fun ari(chapter: Int, verse: Int) = Ari.encode(0, chapter, verse)

    private fun smart(query: String) = SearchEngine.searchByPlan(version, planner.plan(query), allBooksOf(version)).result.toList()

    private fun letters(query: String) = SearchEngine.searchByGrep(version, SearchEngineQuery(query, allBooksOf(version))).toList()

    // Planning

    @Test
    fun `a word listed in the lexicon becomes its whole family`() {
        val t = planner.plan("kasih").single()
        assertEquals(TermKind.FAMILY, t.kind)
        assertEquals(Resolution.LEXICON_DIRECT, t.resolution)
        assertEquals("kasih", t.root)
        assertEquals(listOf("kasih", "mengasihi", "dikasihi"), t.forms)
    }

    @Test
    fun `a derived form typed by the reader resolves to the same family`() {
        val t = planner.plan("mengasihi").single()
        assertEquals("kasih", t.root)
        assertEquals(Resolution.LEXICON_DIRECT, t.resolution)
    }

    @Test
    fun `a word the text never uses is peeled until it reaches a known form`() {
        val t = planner.plan("sembuhkan").single()
        assertEquals(TermKind.FAMILY, t.kind)
        assertEquals(Resolution.LEXICON_PEELED, t.resolution)
        assertEquals("sembuh", t.root)
        assertEquals(listOf("-kan"), t.peelPath.map { it.label })
        assertTrue("the typed word is searched too", "sembuhkan" in t.forms)
    }

    @Test
    fun `a word that occurs but has no relatives is matched exactly and never peeled`() {
        val t = planner.plan("raja").single()
        assertEquals(TermKind.EXACT_WORD, t.kind)
        assertEquals(Resolution.OWN_FAMILY, t.resolution)
        assertEquals("+raja", t.legacyToken)
        assertEquals(1, t.occurrencesInText)
    }

    @Test
    fun `an unknown fragment falls back to the letter search`() {
        val t = planner.plan("bagai").single()
        assertEquals(TermKind.SUBSTRING, t.kind)
        assertEquals(Resolution.FALLBACK_LETTERS, t.resolution)
    }

    @Test
    fun `plus and quotes keep their exact meaning`() {
        val (exact, phrase) = planner.plan("+kasih \"kasih itu\"")
        assertEquals(TermKind.EXACT_WORD, exact.kind)
        assertEquals(Resolution.EXPLICIT_EXACT, exact.resolution)
        assertEquals(TermKind.PHRASE, phrase.kind)
        assertEquals(Resolution.EXPLICIT_PHRASE, phrase.resolution)
    }

    @Test
    fun `terms with digits are matched as letters`() {
        assertEquals(Resolution.NOT_A_WORD, planner.plan("kasih2").single().resolution)
    }

    @Test
    fun `without a lexicon every term keeps the classic behavior`() {
        val t = SmartSearchPlanner(null, null).plan("kasih").single()
        assertEquals(TermKind.SUBSTRING, t.kind)
        assertEquals(Resolution.SMART_OFF, t.resolution)
    }

    @Test
    fun `forms the typed letters cannot reach are the ones a letter search misses`() {
        assertEquals(listOf("mengasihi"), planner.plan("kasih").single().formsUnreachableByLetters)
    }

    // Searching

    @Test
    fun `berkat no longer finds berkata while still finding every form of berkat`() {
        assertEquals(listOf(ari(1, 2), ari(1, 3), ari(2, 3)), letters("berkat"))
        assertEquals(listOf(ari(1, 3), ari(2, 3)), smart("berkat"))
    }

    @Test
    fun `kasih finds mengasihi and leaves out kasihan, a word the lexicon does not put in its family`() {
        assertEquals(listOf(ari(1, 6), ari(2, 1)), letters("kasih"))
        assertEquals(listOf(ari(1, 1), ari(2, 1)), smart("kasih"))
    }

    @Test
    fun `two terms each widen to their family before the results are intersected`() {
        assertEquals(emptyList<Int>(), letters("sembuhkan sakit"))
        assertEquals(listOf(ari(1, 4), ari(1, 5)), smart("sembuhkan sakit"))
    }

    @Test
    fun `iman stops matching the letters inside bagaimana`() {
        assertEquals(listOf(ari(2, 2)), smart("iman"))
    }

    @Test
    fun `a hyphenated form listed in the lexicon matches whole`() {
        assertEquals(listOf(ari(1, 1), ari(1, 2)), smart("kata"))
    }

    @Test
    fun `the book filter applies to family terms`() {
        val none = android.util.SparseBooleanArray()
        assertEquals(0, SearchEngine.searchByPlan(version, planner.plan("kasih"), none).result.size())
    }

    @Test
    fun `each term reports what it matched on its own`() {
        val outcome = SearchEngine.searchByPlan(version, planner.plan("sembuhkan sakit"), allBooksOf(version))
        assertEquals(listOf(2, 2), outcome.terms.map { it.aris.size() })
    }

    // Highlighting and filtering

    @Test
    fun `highlighting marks the family form that matched, as a whole word`() {
        val rt = SearchEngine.ReadyTokens.fromPlan(planner.plan("kasih"))
        val text = "Aku mengasihi engkau, bukan kasihan."
        val spanned: Spanned = SearchEngine.hilite(text, rt, 0xffff0000.toInt())
        val bold = spanned.getSpans(0, spanned.length, StyleSpan::class.java).map { text.substring(spanned.getSpanStart(it), spanned.getSpanEnd(it)) }
        assertEquals(listOf("mengasihi"), bold)
    }

    @Test
    fun `satisfiesTokens understands family terms`() {
        val rt = SearchEngine.ReadyTokens.fromPlan(planner.plan("kasih"))
        assertTrue(SearchEngine.satisfiesTokens("aku mengasihi engkau", rt))
        assertFalse(SearchEngine.satisfiesTokens("belas kasihan", rt))
    }

    @Test
    fun `a family matcher lists the distinct matching words of a verse`() {
        val m = planner.plan("berkat").single().matcher!!
        assertEquals(listOf("diberkati", "berkat"), m.matchesIn("engkau diberkati dan menjadi berkat, berkat."))
    }

    @Test
    fun `the report tells gained verses from dropped ones`() {
        val plan = planner.plan("kasih")
        val outcome = SearchEngine.searchByPlan(version, plan, allBooksOf(version))
        val report = SmartSearchReport(
            query = "kasih",
            versionId = "test",
            versionName = "TEST",
            selection = LexiconSelection(lexicon, SelectionReason.BUILT_IN, vocabulary),
            terms = outcome.terms,
            result = outcome.result,
            selectMillis = 0,
            vocabularyMillis = -1,
            planMillis = 0,
            searchMillis = 0,
            classic = IntArrayList().apply { letters("kasih").forEach { add(it) } },
            classicMillis = 0,
        )
        assertEquals(listOf(ari(1, 1)), report.gained.toList())
        assertEquals(listOf(ari(1, 6)), report.dropped.toList())
        assertTrue(report.isGained(ari(1, 1)))
        assertFalse(report.isGained(ari(2, 1)))
        assertTrue(report.widened)
    }

    // Lexicon from the version

    @Test
    fun `the version's own lexicon is used when it carries one`() {
        val v = versionOf(mapOf((0 to 1) to listOf("Aku mengasihi engkau.")), lexicon = familiesOf("kasih kasih mengasihi"))
        val lex = LexiconRepository.builtInFor(v, "with-lexicon")!!
        assertEquals(LexiconOrigin.VERSION, lex.origin)
        assertEquals("kasih", lex.rootOf["mengasihi"])
    }

    @Test
    fun `a version without a lexicon has no built-in one`() {
        assertNull(LexiconRepository.builtInFor(version, "without-lexicon"))
    }

    // Rules-only lexicon

    @Test
    fun `the rules-only lexicon groups derived words under the deepest stem the text uses`() {
        val rules = RulesLexiconBuilder.build(vocabulary)
        assertEquals("kasih", rules.rootOf["mengasihi"])
        assertEquals("berkat", rules.rootOf["diberkati"])
        assertEquals("berkat", rules.rootOf["berkatilah"])
        assertEquals("kata", rules.rootOf["berkata"])
        assertEquals(LexiconOrigin.RULES, rules.origin)
    }

    @Test
    fun `a word alone in its family gets no entry in the rules-only lexicon`() {
        val rules = RulesLexiconBuilder.build(vocabulary)
        assertNull(rules.families["raja"])
    }

    @Test
    fun `vocabulary counts hyphenated words whole and their parts separately`() {
        assertEquals(1, vocabulary.counts["kata-nya"])
        assertTrue("kata" in vocabulary)
        assertEquals(2, vocabulary.countOf("mereka"))
    }
}
