package yuku.alkitab.base.util

import android.util.SparseBooleanArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.model.Book
import yuku.alkitab.model.FootnoteEntry
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.model.XrefEntry
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

/**
 * Unit tests for [SearchEngine].
 *
 * Covered:
 *  - [SearchEngine.ReadyTokens] construction (single token, whole-word, quoted phrases)
 *  - [SearchEngine.satisfiesTokens] for single-token, multi-token intersection,
 *    whole-word matching, and quoted-phrase (multiword) matching
 *  - [SearchEngine.searchByGrep] end-to-end with a small in-memory fake [Version]:
 *    single-token, multi-token intersection, quoted phrases, whole-word matching,
 *    book filtering, and de-duplication of repeated tokens.
 *
 * Runs under Robolectric so [android.util.SparseBooleanArray] is a real implementation
 * rather than the "not mocked" stub. Test-scope shadows for `android.util.Log` and
 * `FirebaseCrashlytics` (under `src/test/java/…`) keep [AppLog] quiet without extra setup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13 max; project targetSdkVersion is 35
class SearchEngineTest {

    // =========================================================================
    // ReadyTokens construction
    // =========================================================================

    @Test
    fun `an empty token list produces a ReadyTokens with zero count and empty arrays`() {
        val rt = SearchEngine.ReadyTokens(emptyArray())
        assertEquals(0, rt.tokenCount)
        assertEquals(0, rt.tokens.size)
        assertEquals(0, rt.hasPlusses.size)
        assertEquals(0, rt.multiwordsTokens.size)
    }

    @Test
    fun `a single non-plussed token is stored verbatim with no multiword split`() {
        val rt = SearchEngine.ReadyTokens(arrayOf("word"))
        assertEquals(1, rt.tokenCount)
        assertEquals("word", rt.tokens[0])
        assertFalse(rt.hasPlusses[0])
        assertNull(rt.multiwordsTokens[0])
    }

    @Test
    fun `a plussed single-word token has its plus stripped and no multiword split`() {
        val rt = SearchEngine.ReadyTokens(arrayOf("+hello"))
        assertEquals(1, rt.tokenCount)
        assertTrue(rt.hasPlusses[0])
        assertEquals("hello", rt.tokens[0])
        assertNull(rt.multiwordsTokens[0])
    }

    @Test
    fun `a plussed multiword token is split into its constituent words`() {
        val rt = SearchEngine.ReadyTokens(arrayOf("+hello world"))
        assertEquals(1, rt.tokenCount)
        assertTrue(rt.hasPlusses[0])
        assertEquals("hello world", rt.tokens[0])
        assertNotNull(rt.multiwordsTokens[0])
        assertArrayEquals(arrayOf("hello", "world"), rt.multiwordsTokens[0])
    }

    @Test
    fun `a mixture of plussed and non-plussed tokens are each classified independently`() {
        val rt = SearchEngine.ReadyTokens(arrayOf("foo", "+bar", "+baz qux"))
        assertEquals(3, rt.tokenCount)

        assertFalse(rt.hasPlusses[0])
        assertEquals("foo", rt.tokens[0])
        assertNull(rt.multiwordsTokens[0])

        assertTrue(rt.hasPlusses[1])
        assertEquals("bar", rt.tokens[1])
        assertNull(rt.multiwordsTokens[1])

        assertTrue(rt.hasPlusses[2])
        assertEquals("baz qux", rt.tokens[2])
        assertArrayEquals(arrayOf("baz", "qux"), rt.multiwordsTokens[2])
    }

    @Test
    fun `multiword tokenization treats a lone dash as its own word because dash is in the letter class`() {
        // Multiword tokenization splits on non-letter-or-digit. Single quote (') and
        // dash (-) are treated as letters per the [\p{javaLetterOrDigit}'-]+ pattern,
        // so a standalone '-' surrounded by punctuation/space becomes its own word.
        val rt = SearchEngine.ReadyTokens(arrayOf("+abc.,- def123"))
        assertEquals(1, rt.tokenCount)
        assertTrue(rt.hasPlusses[0])
        assertArrayEquals(arrayOf("abc", "-", "def123"), rt.multiwordsTokens[0])
    }

    @Test
    fun `apostrophes and hyphens embedded inside words are kept as part of the word`() {
        val rt = SearchEngine.ReadyTokens(arrayOf("+don't self-aware"))
        assertEquals(1, rt.tokenCount)
        assertTrue(rt.hasPlusses[0])
        assertArrayEquals(arrayOf("don't", "self-aware"), rt.multiwordsTokens[0])
    }

    // =========================================================================
    // satisfiesTokens — single non-plussed token
    // =========================================================================

    private fun tokens(vararg ts: String) = SearchEngine.ReadyTokens(ts.map { it }.toTypedArray())

    @Test
    fun `a non-plussed token matches as a substring inside a larger word`() {
        val rt = tokens("word")
        assertTrue(SearchEngine.satisfiesTokens("swords of glory", rt))
        assertTrue(SearchEngine.satisfiesTokens("the word", rt))
        assertTrue(SearchEngine.satisfiesTokens("word", rt))
    }

    @Test
    fun `a non-plussed token that is absent yields false including against an empty string`() {
        val rt = tokens("apple")
        assertFalse(SearchEngine.satisfiesTokens("banana orange", rt))
        assertFalse(SearchEngine.satisfiesTokens("", rt))
    }

    @Test
    fun `satisfiesTokens is case-sensitive and callers must lowercase both sides first`() {
        val rt = tokens("word")
        assertFalse(SearchEngine.satisfiesTokens("WORD only uppercase", rt))
        assertTrue(SearchEngine.satisfiesTokens("word only lowercase", rt))
    }

    // =========================================================================
    // satisfiesTokens — multi-token (AND semantics / "intersection")
    // =========================================================================

    @Test
    fun `multiple tokens must all appear somewhere in the string for satisfiesTokens to return true`() {
        val rt = tokens("quick", "fox")
        assertTrue(SearchEngine.satisfiesTokens("the quick brown fox jumps", rt))
    }

    @Test
    fun `token order does not matter for multi-token matching`() {
        val rt = tokens("fox", "quick")
        assertTrue(SearchEngine.satisfiesTokens("the quick brown fox jumps", rt))
    }

    @Test
    fun `multi-token matching fails if any single token is missing`() {
        val rt = tokens("quick", "slow")
        assertFalse(SearchEngine.satisfiesTokens("the quick brown fox jumps", rt))
    }

    @Test
    fun `multi-token matching fails when none of the tokens appear`() {
        val rt = tokens("xyz", "abc")
        assertFalse(SearchEngine.satisfiesTokens("the quick brown fox jumps", rt))
    }

    // =========================================================================
    // satisfiesTokens — whole-word matching (plussed single word)
    // =========================================================================

    @Test
    fun `a plussed token matches only when the word boundary is non-letter on both sides`() {
        val rt = tokens("+word")
        assertTrue(SearchEngine.satisfiesTokens("word", rt))
        assertTrue(SearchEngine.satisfiesTokens("a word here", rt))
        assertTrue(SearchEngine.satisfiesTokens("word.", rt))
        assertTrue(SearchEngine.satisfiesTokens(",word,", rt))
    }

    @Test
    fun `a plussed token does not match when it is only a substring of a larger word`() {
        val rt = tokens("+word")
        assertFalse(SearchEngine.satisfiesTokens("sword", rt))
        assertFalse(SearchEngine.satisfiesTokens("swords", rt))
        assertFalse(SearchEngine.satisfiesTokens("words", rt))
        assertFalse(SearchEngine.satisfiesTokens("a swords here", rt))
    }

    @Test
    fun `a plussed token matches at the start or end of the string where the boundary is implicit`() {
        val rt = tokens("+apple")
        assertTrue(SearchEngine.satisfiesTokens("apple pie", rt))
        assertTrue(SearchEngine.satisfiesTokens("my apple", rt))
    }

    @Test
    fun `a plussed token allows a formatting-code letter at pos-1 when pos-2 is the at sign`() {
        // Special case: if pos-2 == '@', the word is preceded by a formatting-tag letter,
        // which is still considered a valid whole-word boundary.
        val rt = tokens("+word")
        assertTrue(SearchEngine.satisfiesTokens("a@9word", rt))
    }

    @Test
    fun `a plussed token rejects a preceding letter that is not a formatting-tag code`() {
        val rt = tokens("+word")
        assertFalse(SearchEngine.satisfiesTokens("xword", rt))
    }

    // =========================================================================
    // satisfiesTokens — combined plussed + non-plussed tokens
    // =========================================================================

    @Test
    fun `mixing a non-plussed token with a plussed token enforces both rules independently`() {
        val rt = tokens("quick", "+fox")
        // "fox" must appear as whole word; "quick" can appear as substring
        assertTrue(SearchEngine.satisfiesTokens("quickly the fox runs", rt))
        // "fox" as a substring but not whole word: should fail (e.g., "foxes")
        assertFalse(SearchEngine.satisfiesTokens("quickly the foxes run", rt))
    }

    // =========================================================================
    // satisfiesTokens — Unicode text
    // =========================================================================

    @Test
    fun `a non-plussed token in non-ASCII script is found as a substring`() {
        val rt = tokens("привет")
        assertTrue(SearchEngine.satisfiesTokens("это привет мир", rt))
        assertFalse(SearchEngine.satisfiesTokens("это пока мир", rt))
    }

    @Test
    fun `plussed whole-word matching respects Unicode letter boundaries`() {
        val rt = tokens("+kasih")
        assertTrue(SearchEngine.satisfiesTokens("dengan kasih tuhan", rt))
        assertFalse(SearchEngine.satisfiesTokens("kekasihku", rt))
    }

    // =========================================================================
    // satisfiesTokens — edge cases
    // =========================================================================

    @Test
    fun `a ReadyTokens with zero tokens is vacuously satisfied by any string`() {
        val rt = SearchEngine.ReadyTokens(emptyArray())
        assertTrue(SearchEngine.satisfiesTokens("anything", rt))
        assertTrue(SearchEngine.satisfiesTokens("", rt))
    }

    @Test
    fun `a plussed token at position zero is always a valid left boundary`() {
        val rt = tokens("+hello")
        assertTrue(SearchEngine.satisfiesTokens("hello world", rt))
    }

    @Test
    fun `a plussed token reaching the end of the string is always a valid right boundary`() {
        val rt = tokens("+world")
        assertTrue(SearchEngine.satisfiesTokens("hello world", rt))
    }

    @Test
    fun `repeated matches of the same non-plussed token are fine — one occurrence suffices`() {
        val rt = tokens("ab")
        assertTrue(SearchEngine.satisfiesTokens("abab ab ab", rt))
    }

    @Test
    fun `digits adjacent to a plussed token count as letter-or-digit and break the boundary`() {
        val rt = tokens("+word")
        assertFalse(SearchEngine.satisfiesTokens("1word", rt))
        assertFalse(SearchEngine.satisfiesTokens("word1", rt))
    }

    // =========================================================================
    // satisfiesTokens — quoted phrase (multiword plussed token)
    // =========================================================================

    @Test
    fun `a quoted phrase matches an exact consecutive sequence of words`() {
        val rt = tokens("+brown fox")
        assertTrue(SearchEngine.satisfiesTokens("the quick brown fox jumps", rt))
        assertTrue(SearchEngine.satisfiesTokens("brown fox runs fast", rt))
    }

    @Test
    fun `a quoted phrase tolerates punctuation and extra spaces between its words`() {
        val rt = tokens("+brown fox")
        // The "consume" loop skips non-letter chars between words.
        assertTrue(SearchEngine.satisfiesTokens("brown, fox", rt))
        assertTrue(SearchEngine.satisfiesTokens("brown  fox", rt))
    }

    @Test
    fun `a quoted phrase steps over inline formatting codes between its words`() {
        val rt = tokens("+brown fox")
        // Single-letter formatting code @9 between the two words should be skipped.
        assertTrue(SearchEngine.satisfiesTokens("brown@9 fox", rt))
        assertTrue(SearchEngine.satisfiesTokens("brown @9fox", rt))
        // Tag opener @<tag@> (no content) between words is stepped over as well.
        assertTrue(SearchEngine.satisfiesTokens("brown@<tag@> fox", rt))
        // Closing tag marker @/ between the two words should also be skipped
        // (it is consumed by the same single-letter-formatting-code branch).
        assertTrue(SearchEngine.satisfiesTokens("brown@/ fox", rt))
        assertTrue(SearchEngine.satisfiesTokens("brown @/fox", rt))
        // A full tag span @<tag@>...@/ with empty content between the words still matches.
        assertTrue(SearchEngine.satisfiesTokens("brown@<tag@>@/ fox", rt))
    }

    @Test
    fun `a quoted phrase fails when its words appear in the reverse order`() {
        val rt = tokens("+brown fox")
        assertFalse(SearchEngine.satisfiesTokens("fox brown", rt))
    }

    @Test
    fun `a quoted phrase fails when another word is inserted between its words`() {
        val rt = tokens("+brown fox")
        assertFalse(SearchEngine.satisfiesTokens("brown small fox", rt))
    }

    @Test
    fun `a quoted phrase enforces whole-word boundaries on its first and last words`() {
        val rt = tokens("+brown fox")
        // "browns" — first word not matching whole-word boundary.
        assertFalse(SearchEngine.satisfiesTokens("browns fox jumps", rt))
        // Second word in "foxes" — not a whole-word match.
        assertFalse(SearchEngine.satisfiesTokens("brown foxes", rt))
    }

    @Test
    fun `a three-word quoted phrase matches only the exact sequence of three words`() {
        val rt = tokens("+the quick brown")
        assertTrue(SearchEngine.satisfiesTokens("the quick brown fox jumps", rt))
        assertFalse(SearchEngine.satisfiesTokens("quick the brown", rt))
        assertFalse(SearchEngine.satisfiesTokens("the brown", rt))
    }

    // =========================================================================
    // ReadyTokens — round-trip from QueryTokenizer
    // =========================================================================

    @Test
    fun `a double-quoted phrase parsed by QueryTokenizer is recognized as a multiword ReadyToken`() {
        val tokenized = QueryTokenizer.tokenize("\"hello world\"")
        assertArrayEquals(arrayOf("+hello world"), tokenized)

        val rt = SearchEngine.ReadyTokens(tokenized)
        assertEquals(1, rt.tokenCount)
        assertTrue(rt.hasPlusses[0])
        assertEquals("hello world", rt.tokens[0])
        assertArrayEquals(arrayOf("hello", "world"), rt.multiwordsTokens[0])
    }

    @Test
    fun `an unquoted single word parsed by QueryTokenizer is a non-plussed ReadyToken`() {
        val tokenized = QueryTokenizer.tokenize("hello")
        assertArrayEquals(arrayOf("hello"), tokenized)

        val rt = SearchEngine.ReadyTokens(tokenized)
        assertEquals(1, rt.tokenCount)
        assertFalse(rt.hasPlusses[0])
        assertEquals("hello", rt.tokens[0])
        assertNull(rt.multiwordsTokens[0])
    }

    @Test
    fun `a query mixing a quoted phrase and an unquoted word is split into two tokens`() {
        val tokenized = QueryTokenizer.tokenize("\"a b\" c")
        assertArrayEquals(arrayOf("+a b", "c"), tokenized)

        val rt = SearchEngine.ReadyTokens(tokenized)
        assertEquals(2, rt.tokenCount)

        assertTrue(rt.hasPlusses[0])
        assertArrayEquals(arrayOf("a", "b"), rt.multiwordsTokens[0])

        assertFalse(rt.hasPlusses[1])
        assertEquals("c", rt.tokens[1])
        assertNull(rt.multiwordsTokens[1])
    }

    // =========================================================================
    // searchByGrep — end-to-end with a small in-memory fake Version.
    // =========================================================================

    /**
     * Builds a minimal two-book version for testing. The text is plain nature prose —
     * its job is just to give searchByGrep a realistic-shape corpus.
     *
     * Book 0 ("Aaa") has 2 chapters:
     *   1: "the clever fox chased a rabbit / the meadow stood silent / near the fox sat a crow"
     *   2: "fox rested @<ref@>ancient oak@/ / someone whistled / sword below a stone"
     * Book 1 ("Bbb") has 1 chapter:
     *   1: "the fox shows love / the word was spoken by a fox / love your friends dearly"
     *
     * Aaa 2:1 contains a real inline tag span (`@<ref@>ancient oak@/`) so we can verify
     * that search finds tokens appearing inside tag content.
     *
     * Per `Yes2VerseTextDecoder.makeIntoSingleString`, every verse — including the last —
     * is terminated by a `'\n'` in the string returned from
     * [Version.loadChapterTextLowercasedWithoutSplit]. The test data mirrors that.
     */
    private fun fakeVersion(): Version {
        val aaa = Book().apply {
            bookId = 0
            shortName = "Aaa"
            abbreviation = "Aaa"
            chapter_count = 2
            verse_counts = intArrayOf(3, 3)
        }
        val bbb = Book().apply {
            bookId = 1
            shortName = "Bbb"
            abbreviation = "Bbb"
            chapter_count = 1
            verse_counts = intArrayOf(3)
        }

        // Verses are stored already lowercased — searchByGrep consumes lowercased text.
        // Each verse is terminated by '\n', matching the production decoder contract.
        val chapters = mapOf(
            Ari.encode(0, 1, 0) to "the clever fox chased a rabbit\nthe meadow stood silent\nnear the fox sat a crow\n",
            Ari.encode(0, 2, 0) to "fox rested @<ref@>ancient oak@/\nsomeone whistled\nsword below a stone\n",
            Ari.encode(1, 1, 0) to "the fox shows love\nthe word was spoken by a fox\nlove your friends dearly\n",
        )

        return object : Version() {
            override fun getShortName(): String? = "TEST"
            override fun getLongName(): String? = "Test Version"
            override fun getLocale(): String? = "en"
            override fun getMaxBookIdPlusOne(): Int = 2
            override fun getConsecutiveBooks(): Array<Book> = arrayOf(aaa, bbb)
            override fun getBook(bookId: Int): Book? = when (bookId) {
                0 -> aaa
                1 -> bbb
                else -> null
            }
            override fun getFirstBook(): Book = aaa
            override fun loadVerseText(ari: Int): String? = null
            override fun loadVerseText(book: Book?, chapter_1: Int, verse_1: Int): String? = null
            override fun loadVersesByAriRanges(
                ariRanges: IntArrayList?,
                result_aris: IntArrayList?,
                result_verses: MutableList<String>?,
            ): Int = 0
            override fun loadPericope(
                bookId: Int,
                chapter_1: Int,
                aris: IntArrayList?,
                pericopeBlocks: MutableList<PericopeBlock>?,
            ): Int = 0
            override fun loadChapterText(book: Book?, chapter_1: Int): SingleChapterVerses? = null
            override fun loadChapterTextLowercasedWithoutSplit(book: Book, chapter_1: Int): String? {
                return chapters[Ari.encode(book.bookId, chapter_1, 0)]
            }
            override fun getXrefEntry(arif: Int): XrefEntry? = null
            override fun getFootnoteEntry(arif: Int): FootnoteEntry? = null
        }
    }

    private fun allBookIds(vararg ids: Int): SparseBooleanArray {
        val a = SparseBooleanArray()
        for (id in ids) a.put(id, true)
        return a
    }

    private fun IntArrayList.toList(): List<Int> = (0 until size()).map { get(it) }

    private fun query(queryString: String, vararg bookIds: Int): SearchEngineQuery {
        return SearchEngineQuery(queryString, allBookIds(*bookIds))
    }

    @Test
    fun `searchByGrep returns every ARI whose verse contains a single non-plussed token as a substring`() {
        val v = fakeVersion()
        // "fox" appears in Aaa 1:1, Aaa 1:3, Aaa 2:1, Bbb 1:1, Bbb 1:2
        val res = SearchEngine.searchByGrep(v, query("fox", 0, 1))
        assertEquals(
            listOf(
                Ari.encode(0, 1, 1),
                Ari.encode(0, 1, 3),
                Ari.encode(0, 2, 1),
                Ari.encode(1, 1, 1),
                Ari.encode(1, 1, 2),
            ),
            res.toList(),
        )
    }

    @Test
    fun `searchByGrep with a non-plussed token matches substrings inside longer words`() {
        val v = fakeVersion()
        // "word" appears in "sword" (Aaa 2:3) and "word" (Bbb 1:2); non-plussed search is
        // pure substring so both count.
        val res = SearchEngine.searchByGrep(v, query("word", 0, 1))
        assertEquals(
            listOf(Ari.encode(0, 2, 3), Ari.encode(1, 1, 2)),
            res.toList(),
        )
    }

    @Test
    fun `searchByGrep returns an empty result when the token matches no verses`() {
        val v = fakeVersion()
        val res = SearchEngine.searchByGrep(v, query("abracadabra", 0, 1))
        assertEquals(0, res.size())
    }

    @Test
    fun `searchByGrep with multiple tokens intersects results so every token must appear in the same verse`() {
        val v = fakeVersion()
        // "fox" appears in 5 verses; "love" appears in Bbb 1:1 ("the fox shows love") and
        // Bbb 1:3 ("love your friends dearly"). Only Bbb 1:1 has both.
        val res = SearchEngine.searchByGrep(v, query("fox love", 0, 1))
        assertEquals(listOf(Ari.encode(1, 1, 1)), res.toList())
    }

    @Test
    fun `searchByGrep returns empty when two tokens never co-occur in the same verse`() {
        val v = fakeVersion()
        // "sword" only in Aaa 2:3; "love" only in Bbb. No intersection.
        val res = SearchEngine.searchByGrep(v, query("sword love", 0, 1))
        assertEquals(0, res.size())
    }

    @Test
    fun `searchByGrep with a quoted single word enforces whole-word matching`() {
        val v = fakeVersion()
        // Plussed "+word" (via quotes) requires whole-word.
        // "word" whole-word appears in Bbb 1:2 ("the word was spoken by a fox").
        // "sword" in Aaa 2:3 contains "word" but is NOT a whole-word match.
        val res = SearchEngine.searchByGrep(v, query("\"word\"", 0, 1))
        assertEquals(listOf(Ari.encode(1, 1, 2)), res.toList())
    }

    @Test
    fun `searchByGrep whole-word matching still returns every verse containing the target word`() {
        val v = fakeVersion()
        // "+fox" as a whole word. Should still match all 5 verses where "fox" appears as a word.
        val res = SearchEngine.searchByGrep(v, query("\"fox\"", 0, 1))
        assertEquals(
            listOf(
                Ari.encode(0, 1, 1),
                Ari.encode(0, 1, 3),
                Ari.encode(0, 2, 1),
                Ari.encode(1, 1, 1),
                Ari.encode(1, 1, 2),
            ),
            res.toList(),
        )
    }

    @Test
    fun `searchByGrep with a quoted phrase matches only the exact consecutive word sequence`() {
        val v = fakeVersion()
        // "clever fox" appears only in Aaa 1:1 ("the clever fox chased a rabbit").
        val res = SearchEngine.searchByGrep(v, query("\"clever fox\"", 0, 1))
        assertEquals(listOf(Ari.encode(0, 1, 1)), res.toList())
    }

    @Test
    fun `searchByGrep with a quoted phrase does not match across a verse boundary`() {
        val v = fakeVersion()
        // Aaa 1 verses joined: "...a rabbit\nthe meadow..." — "rabbit the" spans the
        // verse boundary. isNewlineDelimitedText=true prevents matches across '\n'.
        val res = SearchEngine.searchByGrep(v, query("\"rabbit the\"", 0, 1))
        assertEquals(0, res.size())
    }

    @Test
    fun `searchByGrep with a quoted phrase that appears nowhere returns empty`() {
        val v = fakeVersion()
        val res = SearchEngine.searchByGrep(v, query("\"unicorns gallop\"", 0, 1))
        assertEquals(0, res.size())
    }

    @Test
    fun `searchByGrep only searches books whose ids are marked true in the book filter`() {
        val v = fakeVersion()
        // Same "fox" query but restrict to book 0 (Aaa) only.
        val res = SearchEngine.searchByGrep(v, query("fox", 0))
        assertEquals(
            listOf(
                Ari.encode(0, 1, 1),
                Ari.encode(0, 1, 3),
                Ari.encode(0, 2, 1),
            ),
            res.toList(),
        )
    }

    @Test
    fun `searchByGrep with an empty book-id filter matches no verses`() {
        val v = fakeVersion()
        val res = SearchEngine.searchByGrep(v, SearchEngineQuery("fox", SparseBooleanArray()))
        assertEquals(0, res.size())
    }

    @Test
    fun `searchByGrep deduplicates repeated tokens so the result is identical to a single-token query`() {
        val v = fakeVersion()
        // "fox fox fox" — duplicate tokens are dropped before searching; result same as single "fox".
        val res = SearchEngine.searchByGrep(v, query("fox fox fox", 0, 1))
        assertEquals(
            listOf(
                Ari.encode(0, 1, 1),
                Ari.encode(0, 1, 3),
                Ari.encode(0, 2, 1),
                Ari.encode(1, 1, 1),
                Ari.encode(1, 1, 2),
            ),
            res.toList(),
        )
    }

    @Test
    fun `searchByGrep returns each matching verse only once even if a token appears more than once in it`() {
        val v = fakeVersion()
        // Bbb 1:1 "the fox shows love" — one "love". Bbb 1:3 "love your friends dearly" — one "love".
        val res = SearchEngine.searchByGrep(v, query("love", 0, 1))
        assertEquals(
            listOf(
                Ari.encode(1, 1, 1),
                Ari.encode(1, 1, 3),
            ),
            res.toList(),
        )
    }

    @Test
    fun `searchByGrep intersects a whole-word token and a substring token per verse`() {
        val v = fakeVersion()
        // "+fox" (whole word) AND "lo" (substring).
        // "+fox" matches all 5 "fox" verses.
        // "lo" substring matches "below" (Aaa 2:3), "love" (Bbb 1:1, 1:3).
        // Intersection: verses containing "fox" (whole word) AND "lo" substring.
        //   Bbb 1:1 "the fox shows love" — has both.
        val res = SearchEngine.searchByGrep(v, query("\"fox\" lo", 0, 1))
        assertEquals(listOf(Ari.encode(1, 1, 1)), res.toList())
    }

    // =========================================================================
    // searchByGrep — searching inside inline tag spans (@<tag@>content@/).
    //
    // Aaa 2:1 contains the tag span `@<ref@>ancient oak@/`. Tokens that appear
    // inside the tag content should still be found — inline formatting codes are
    // stepped over by the multiword consume loop, and non-plussed searches are
    // plain substring matches that don't even look at boundaries.
    // =========================================================================

    @Test
    fun `searchByGrep finds a non-plussed token appearing inside a tag span content`() {
        val v = fakeVersion()
        // "ancient" is only inside @<ref@>ancient oak@/ in Aaa 2:1.
        val res = SearchEngine.searchByGrep(v, query("ancient", 0, 1))
        assertEquals(listOf(Ari.encode(0, 2, 1)), res.toList())
    }

    @Test
    fun `searchByGrep with a plussed whole-word token matches a word inside a tag span`() {
        val v = fakeVersion()
        // "+ancient" — whole-word match. Preceding char is '>' (from @>), following is ' ' —
        // both non-letter-or-digit, so the word boundary check passes.
        val res = SearchEngine.searchByGrep(v, query("\"ancient\"", 0, 1))
        assertEquals(listOf(Ari.encode(0, 2, 1)), res.toList())
    }

    @Test
    fun `searchByGrep with a quoted phrase matches consecutive words inside a tag span`() {
        val v = fakeVersion()
        // "+ancient oak" — the two words of the phrase are both inside the tag content,
        // separated only by a space. The consume loop between phrase words doesn't care
        // whether it's inside or outside a tag.
        val res = SearchEngine.searchByGrep(v, query("\"ancient oak\"", 0, 1))
        assertEquals(listOf(Ari.encode(0, 2, 1)), res.toList())
    }

    @Test
    fun `searchByGrep with a quoted phrase spans from verse text into tag span content`() {
        val v = fakeVersion()
        // "+rested ancient" — "rested" is verse text, "ancient" is inside the tag span.
        // The consume loop between the two steps over the `@<ref@>` tag opener to reach
        // "ancient", so the phrase matches.
        val res = SearchEngine.searchByGrep(v, query("\"rested ancient\"", 0, 1))
        assertEquals(listOf(Ari.encode(0, 2, 1)), res.toList())
    }

    @Test
    fun `searchByGrep with two AND tokens one inside the tag span and one outside both match the same verse`() {
        val v = fakeVersion()
        // "rested" is verse text and "ancient" is inside the tag span. Multi-token AND
        // semantics: both must appear somewhere in the verse, regardless of whether
        // one is inside a tag.
        val res = SearchEngine.searchByGrep(v, query("ancient rested", 0, 1))
        assertEquals(listOf(Ari.encode(0, 2, 1)), res.toList())
    }
}
