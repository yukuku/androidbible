package yuku.alkitab.base.cp

import android.app.Application
import android.content.pm.ProviderInfo
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.S
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.model.Book
import yuku.alkitab.model.FootnoteEntry
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.model.XrefEntry
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import yuku.alkitabintegration.provider.VerseProvider

/**
 * End-to-end tests for the read-only [Provider] content provider. Exercises each
 * supported URI path (single ARI / range ARI / single LID / range LID / version
 * listing) against a tiny in-memory fake [Version] swapped in via
 * [S.setActiveVersion], plus the URI-mismatch and `getType` contracts.
 *
 * Setup notes:
 *  - `@Config(application = Application::class)` avoids running
 *    `yuku.alkitab.base.App.onCreate` (Firebase / FCM). The
 *    [Provider.onCreate] override is also stubbed out in [stubbedProvider]
 *    because it would otherwise call `App.staticInit` directly.
 *  - The first access to `S` triggers `ActiveVersionHolder`'s static init,
 *    which reads `Prefkey.lastVersionId` (null in tests) and falls back to
 *    [yuku.alkitab.base.model.MVersionInternal]. That path constructs a
 *    `VersionImpl` over the placeholder DDD assets but does **not** read any
 *    asset bytes until a method like `loadBooks()` is called, so init completes
 *    without I/O. We then immediately overwrite the active version with our
 *    fake, so production queries route into the fake instead of the DDD reader.
 *  - The test-scope shadows for `android.util.Log` and
 *    `com.google.firebase.crashlytics.FirebaseCrashlytics` under
 *    `Alkitab/src/test/java/` keep `AppLog` quiet without bootstrapping
 *    Firebase, the same trick used by `InternalDbTest` and `SearchEngineTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ProviderTest {

    private lateinit var provider: Provider

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app

        S.setActiveVersion(FakeMVersion(fakeVersion()))

        provider = stubbedProvider()
        provider.attachInfo(app, ProviderInfo().apply { authority = AUTHORITY })
    }

    // region Fake Version

    /**
     * MVersion subclass that returns a pre-built fake [Version] from
     * [MVersion.getVersion]. The fake content is deterministic so every
     * assertion below is exact.
     */
    private class FakeMVersion(private val v: Version) : MVersion() {
        override fun getVersionId(): String = "test/fake"
        override fun getVersion(): Version = v
        override fun getActive(): Boolean = true
        override fun hasDataFile(): Boolean = true
    }

    /**
     * Builds a two-book in-memory version:
     *   - Book 0 ("Gen") — 2 chapters (3 + 4 verses).
     *     Gen 1:3 carries inline formatting codes (`@@`-prefix + `@9...@7`
     *     italics) so we can verify `formatting=0` strips them and
     *     `formatting=1` keeps them.
     *   - Book 1 ("Exo") — 1 chapter (5 verses).
     */
    private fun fakeVersion(): Version {
        val gen = Book().apply {
            bookId = 0
            shortName = "Gen"
            abbreviation = "Gn"
            chapter_count = 2
            verse_counts = intArrayOf(3, 4)
        }
        val exo = Book().apply {
            bookId = 1
            shortName = "Exo"
            abbreviation = "Ex"
            chapter_count = 1
            verse_counts = intArrayOf(5)
        }

        val chapters = mapOf(
            Ari.encode(0, 1, 0) to chapter(
                "In the beginning",
                "God created the heavens and the earth",
                // `@@` prefix marks the verse as formatted; `@9...@7` is an italic span.
                "@@@9And the earth was without form@7 and void",
            ),
            Ari.encode(0, 2, 0) to chapter(
                "Thus the heavens were finished",
                "And on the seventh day God ended His work",
                "And He blessed the seventh day",
                "These are the generations of the heavens",
            ),
            Ari.encode(1, 1, 0) to chapter(
                "These are the names of the children of Israel",
                "Reuben Simeon Levi and Judah",
                "Issachar Zebulun and Benjamin",
                "Dan and Naphtali Gad and Asher",
                "And all the souls who were born to Jacob",
            ),
        )

        return object : Version() {
            override fun getShortName(): String = "FK"
            override fun getLongName(): String = "Fake Version"
            override fun getLocale(): String = "en"
            override fun getMaxBookIdPlusOne(): Int = 2
            override fun getConsecutiveBooks(): Array<Book> = arrayOf(gen, exo)
            override fun getBook(bookId: Int): Book? = when (bookId) {
                0 -> gen
                1 -> exo
                else -> null
            }
            override fun getFirstBook(): Book = gen
            override fun loadVerseText(ari: Int): String? {
                val book = getBook(Ari.toBook(ari)) ?: return null
                return loadVerseText(book, Ari.toChapter(ari), Ari.toVerse(ari))
            }
            override fun loadVerseText(book: Book?, chapter_1: Int, verse_1: Int): String? {
                if (book == null) return null
                val verses = loadChapterText(book, chapter_1) ?: return null
                val v0 = verse_1 - 1
                if (v0 < 0 || v0 >= verses.verseCount) return null
                return verses.getVerse(v0)
            }
            override fun loadVersesByAriRanges(
                ariRanges: IntArrayList,
                result_aris: IntArrayList,
                result_verses: MutableList<String>,
            ): Int = 0
            override fun loadPericope(
                bookId: Int,
                chapter_1: Int,
                aris: IntArrayList,
                pericopeBlocks: MutableList<PericopeBlock>,
            ): Int = 0
            override fun loadChapterText(book: Book?, chapter_1: Int): SingleChapterVerses? {
                if (book == null) return null
                return chapters[Ari.encode(book.bookId, chapter_1, 0)]
            }
            override fun loadChapterTextLowercasedWithoutSplit(book: Book, chapter_1: Int): String? = null
            override fun getXrefEntry(arif: Int): XrefEntry? = null
            override fun getFootnoteEntry(arif: Int): FootnoteEntry? = null
        }
    }

    private fun chapter(vararg verses: String): SingleChapterVerses = object : SingleChapterVerses {
        override val verseCount: Int get() = verses.size
        override fun getVerse(verse_0: Int): String = verses[verse_0]
    }

    // endregion

    // region URI helpers

    private fun singleAriUri(ari: Int): Uri =
        Uri.parse("content://$AUTHORITY/${VerseProvider.PATH_bible_verses_single_by_ari}$ari")

    private fun singleLidUri(lid: Int): Uri =
        Uri.parse("content://$AUTHORITY/${VerseProvider.PATH_bible_verses_single_by_lid}$lid")

    private fun rangeAriUri(range: String): Uri =
        Uri.parse("content://$AUTHORITY/${VerseProvider.PATH_bible_verses_range_by_ari}$range")

    private fun rangeLidUri(range: String): Uri =
        Uri.parse("content://$AUTHORITY/${VerseProvider.PATH_bible_verses_range_by_lid}$range")

    private val versionsUri: Uri get() = Uri.parse("content://$AUTHORITY/bible/versions")

    // endregion

    // region Tests — single verse by ARI

    @Test
    fun `single-verse ARI query returns one row with the verse text, book short name, and the queried ARI`() {
        val ari = Ari.encode(0, 1, 1) // Gen 1:1
        val cursor = provider.query(singleAriUri(ari), null, null, null, null)
        assertNotNull(cursor)
        cursor!!.use {
            assertEquals(1, it.count)
            assertTrue(it.moveToNext())
            assertEquals(ari, it.getInt(it.getColumnIndexOrThrow(VerseProvider.COLUMN_ari)))
            assertEquals("Gen", it.getString(it.getColumnIndexOrThrow(VerseProvider.COLUMN_bookName)))
            assertEquals("In the beginning", it.getString(it.getColumnIndexOrThrow(VerseProvider.COLUMN_text)))
            // Single-verse path also writes a literal `1` for `_id` regardless of the verse.
            assertEquals(1L, it.getLong(it.getColumnIndexOrThrow("_id")))
        }
    }

    @Test
    fun `single-verse ARI query strips inline formatting codes by default`() {
        // Gen 1:3 has @@@9...@7... inline codes. With the default formatting=0
        // the provider invokes FormattedVerseText.removeSpecialCodes, which
        // unwraps the italic span and drops the @@ prefix.
        val ari = Ari.encode(0, 1, 3)
        val cursor = provider.query(singleAriUri(ari), null, null, null, null)!!
        cursor.use {
            assertTrue(it.moveToNext())
            assertEquals(
                "And the earth was without form and void",
                it.getString(it.getColumnIndexOrThrow(VerseProvider.COLUMN_text)),
            )
        }
    }

    @Test
    fun `single-verse ARI query with formatting=1 returns the raw verse text including codes`() {
        val ari = Ari.encode(0, 1, 3)
        val uri = singleAriUri(ari).buildUpon().appendQueryParameter("formatting", "1").build()
        val cursor = provider.query(uri, null, null, null, null)!!
        cursor.use {
            assertTrue(it.moveToNext())
            assertEquals(
                "@@@9And the earth was without form@7 and void",
                it.getString(it.getColumnIndexOrThrow(VerseProvider.COLUMN_text)),
            )
        }
    }

    @Test
    fun `single-verse ARI query for an unknown book returns an empty cursor not null`() {
        // bookId 99 is not part of our fake version, so getBook returns null
        // and the single-verse path adds no row but still returns the empty
        // MatrixCursor it built — never null.
        val ari = Ari.encode(99, 1, 1)
        val cursor = provider.query(singleAriUri(ari), null, null, null, null)
        assertNotNull(cursor)
        cursor!!.use { assertEquals(0, it.count) }
    }

    // endregion

    // region Tests — range by ARI

    @Test
    fun `range ARI query within a single chapter returns the expected verses in order`() {
        // Gen 1:1..1:3 → all three verses of Gen 1. Formatting codes on 1:3 are stripped.
        val start = Ari.encode(0, 1, 1)
        val end = Ari.encode(0, 1, 3)
        val cursor = provider.query(rangeAriUri("$start-$end"), null, null, null, null)!!
        cursor.use {
            assertEquals(3, it.count)
            val rows = mutableListOf<Triple<Int, String, String>>()
            while (it.moveToNext()) {
                rows.add(
                    Triple(
                        it.getInt(it.getColumnIndexOrThrow(VerseProvider.COLUMN_ari)),
                        it.getString(it.getColumnIndexOrThrow(VerseProvider.COLUMN_bookName)),
                        it.getString(it.getColumnIndexOrThrow(VerseProvider.COLUMN_text)),
                    ),
                )
            }
            assertEquals(
                listOf(
                    Triple(Ari.encode(0, 1, 1), "Gen", "In the beginning"),
                    Triple(Ari.encode(0, 1, 2), "Gen", "God created the heavens and the earth"),
                    Triple(Ari.encode(0, 1, 3), "Gen", "And the earth was without form and void"),
                ),
                rows,
            )
        }
    }

    @Test
    fun `range ARI query that spans a chapter boundary returns verses from both chapters in order`() {
        // Gen 1:2 → Gen 2:2. Crosses the chapter boundary in Genesis.
        val start = Ari.encode(0, 1, 2)
        val end = Ari.encode(0, 2, 2)
        val cursor = provider.query(rangeAriUri("$start-$end"), null, null, null, null)!!
        cursor.use {
            assertEquals(4, it.count)
            val pairs = mutableListOf<Pair<Int, String>>()
            while (it.moveToNext()) {
                pairs.add(
                    it.getInt(it.getColumnIndexOrThrow(VerseProvider.COLUMN_ari)) to
                        it.getString(it.getColumnIndexOrThrow(VerseProvider.COLUMN_text)),
                )
            }
            assertEquals(
                listOf(
                    Ari.encode(0, 1, 2) to "God created the heavens and the earth",
                    Ari.encode(0, 1, 3) to "And the earth was without form and void",
                    Ari.encode(0, 2, 1) to "Thus the heavens were finished",
                    Ari.encode(0, 2, 2) to "And on the seventh day God ended His work",
                ),
                pairs,
            )
        }
    }

    @Test
    fun `range ARI query that crosses a book boundary returns verses from both books`() {
        // Gen 2:4 → Exo 1:2. Different books, different chapters.
        val start = Ari.encode(0, 2, 4)
        val end = Ari.encode(1, 1, 2)
        val cursor = provider.query(rangeAriUri("$start-$end"), null, null, null, null)!!
        cursor.use {
            assertEquals(3, it.count)
            val pairs = mutableListOf<Pair<Int, String>>()
            while (it.moveToNext()) {
                pairs.add(
                    it.getInt(it.getColumnIndexOrThrow(VerseProvider.COLUMN_ari)) to
                        it.getString(it.getColumnIndexOrThrow(VerseProvider.COLUMN_bookName)),
                )
            }
            assertEquals(
                listOf(
                    Ari.encode(0, 2, 4) to "Gen",
                    Ari.encode(1, 1, 1) to "Exo",
                    Ari.encode(1, 1, 2) to "Exo",
                ),
                pairs,
            )
        }
    }

    @Test
    fun `range ARI query for an unknown book returns an empty cursor not null`() {
        // bookId 99 → getBook returns null; the inner block is skipped, leaving an empty cursor.
        val start = Ari.encode(99, 1, 1)
        val end = Ari.encode(99, 1, 5)
        val cursor = provider.query(rangeAriUri("$start-$end"), null, null, null, null)
        assertNotNull("out-of-range range query must return an empty cursor, not null", cursor)
        cursor!!.use { assertEquals(0, it.count) }
    }

    @Test
    fun `range ARI query with a whole-chapter shorthand X-X expands to all verses in that chapter`() {
        // decodeAriRange treats "start == end && verse(start) == 0" as a whole
        // chapter: the start gets ORed with 0x01 and the end with 0xff.
        // For Gen 2 (4 verses), that should yield exactly Gen 2:1..2:4.
        val bc = Ari.encode(0, 2, 0) // verse=0 ⇒ whole-chapter shorthand
        val cursor = provider.query(rangeAriUri("$bc-$bc"), null, null, null, null)!!
        cursor.use {
            assertEquals(4, it.count)
            val aris = mutableListOf<Int>()
            while (it.moveToNext()) {
                aris.add(it.getInt(it.getColumnIndexOrThrow(VerseProvider.COLUMN_ari)))
            }
            assertEquals(
                listOf(
                    Ari.encode(0, 2, 1),
                    Ari.encode(0, 2, 2),
                    Ari.encode(0, 2, 3),
                    Ari.encode(0, 2, 4),
                ),
                aris,
            )
        }
    }

    // endregion

    // region Tests — by LID

    @Test
    fun `single-verse LID query resolves the LID to its ARI and returns the matching verse`() {
        // Per LidToAri, lid=2 → ARI 0x000102 = Gen 1:2.
        val cursor = provider.query(singleLidUri(2), null, null, null, null)!!
        cursor.use {
            assertEquals(1, it.count)
            assertTrue(it.moveToNext())
            assertEquals(
                Ari.encode(0, 1, 2),
                it.getInt(it.getColumnIndexOrThrow(VerseProvider.COLUMN_ari)),
            )
            assertEquals(
                "God created the heavens and the earth",
                it.getString(it.getColumnIndexOrThrow(VerseProvider.COLUMN_text)),
            )
        }
    }

    @Test
    fun `range LID query is delegated through lidToAri and returns the corresponding ARI verses`() {
        // lid 1..3 → ARI 0x000101..0x000103 (Gen 1:1..1:3).
        val cursor = provider.query(rangeLidUri("1-3"), null, null, null, null)!!
        cursor.use {
            assertEquals(3, it.count)
            val aris = mutableListOf<Int>()
            while (it.moveToNext()) {
                aris.add(it.getInt(it.getColumnIndexOrThrow(VerseProvider.COLUMN_ari)))
            }
            assertEquals(
                listOf(
                    Ari.encode(0, 1, 1),
                    Ari.encode(0, 1, 2),
                    Ari.encode(0, 1, 3),
                ),
                aris,
            )
        }
    }

    // endregion

    // region Tests — version listing

    @Test
    fun `versions listing query returns at least the internal version with fields from AppConfig`() {
        val cursor = provider.query(versionsUri, null, null, null, null)
        assertNotNull(cursor)
        cursor!!.use {
            // Internal version is always present as row 1. The placeholder DDD
            // database has no other versions seeded in unit tests, so we only
            // assert against the internal row to stay robust if the test JVM
            // ever picks up stray DB state from another test class.
            assertTrue("versions cursor should have at least one row", it.count >= 1)
            assertTrue(it.moveToNext())
            assertEquals(1L, it.getLong(it.getColumnIndexOrThrow("_id")))
            assertEquals("internal", it.getString(it.getColumnIndexOrThrow("type")))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow("available")))
            val ac = AppConfig.get()
            assertEquals(ac.internalShortName, it.getString(it.getColumnIndexOrThrow("shortName")))
            assertEquals(ac.internalLongName, it.getString(it.getColumnIndexOrThrow("longName")))
            assertEquals(ac.internalLongName, it.getString(it.getColumnIndexOrThrow("description")))
        }
    }

    // endregion

    // region Tests — URI / contract

    @Test
    fun `unknown URI path returns null per the ContentProvider contract`() {
        val uri = Uri.parse("content://$AUTHORITY/totally/unknown/path")
        assertNull(provider.query(uri, null, null, null, null))
    }

    @Test
    fun `getType returns null for every URI path because the provider does not declare any MIME type`() {
        // Provider.getType is hardcoded to return null. This test pins that
        // contract so a future refactor doesn't silently start advertising a
        // MIME type without test coverage.
        val ari = Ari.encode(0, 1, 1)
        assertNull(provider.getType(singleAriUri(ari)))
        assertNull(provider.getType(singleLidUri(1)))
        assertNull(provider.getType(rangeAriUri("$ari-$ari")))
        assertNull(provider.getType(rangeLidUri("1-3")))
        assertNull(provider.getType(versionsUri))
        assertNull(provider.getType(Uri.parse("content://$AUTHORITY/unknown/path")))
    }

    // endregion

    companion object {
        /**
         * Authority is intentionally distinct from the production
         * `<applicationId>.provider` so tests never collide with a real
         * installation. The static UriMatcher is set up on first attachInfo and
         * reused thereafter, so every test in this class must use the same
         * authority.
         */
        private const val AUTHORITY = "yuku.alkitab.test.provider"

        /**
         * Returns a Provider subclass whose `onCreate` is a no-op. The
         * production `onCreate` calls `yuku.alkitab.base.App.staticInit`, which
         * bootstraps FCM, FeedbackSender, and preference
         * defaults — none of which we need here, and most of which would not
         * work under plain Robolectric anyway. `attachInfo` (super) still calls
         * this overridden `onCreate`, and `Provider.attachInfo` (subclass)
         * still runs its UriMatcher setup after super.
         */
        private fun stubbedProvider(): Provider = object : Provider() {
            override fun onCreate(): Boolean = true
        }
    }
}
