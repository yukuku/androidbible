package yuku.alkitab.base.storage

import android.app.Application
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.storage.room.SongInfoEntity
import yuku.alkitab.base.storage.room.SongRoomDatabase
import yuku.alkitab.songs.SongBookUtil
import yuku.alkitab.songs.newdoc.Block
import yuku.alkitab.songs.newdoc.Line
import yuku.alkitab.songs.newdoc.LyricBlock
import yuku.alkitab.songs.newdoc.PBlock
import yuku.alkitab.songs.newdoc.RowBlock
import yuku.alkitab.songs.newdoc.ScriptureBlock
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.SongDocumentJson
import yuku.alkitab.songs.newdoc.Verse
import yuku.alkitab.songs.newdoc.VerseKind
import yuku.alkitab.songs.newdoc.VerseLine
import yuku.kpri.model.Song

/**
 * Robolectric tests for the [SongDb] facade. The facade routes through
 * Room (see `Alkitab/src/main/java/yuku/alkitab/base/storage/SongDb.java`);
 * the test installs an in-memory [SongRoomDatabase] in [setUp] via
 * [SongRoomDatabase.setForTesting].
 *
 * The payload is UTF-8 JSON ([yuku.alkitab.songs.newdoc.SongDocumentJson]);
 * [SongDocument] is a data class hierarchy, so most assertions here are
 * plain `assertEquals` on the whole document. The one Parcelable-specific
 * test left is `getSong lazily converts a legacy Parcelable row`, which
 * exercises the on-read conversion path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SongDbTest {
    private lateinit var helper: SongDbHelper
    private lateinit var roomDb: SongRoomDatabase
    private lateinit var dao: SongDb

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        helper = SongDbHelper()
        roomDb = Room.inMemoryDatabaseBuilder(app, SongRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SongRoomDatabase.setForTesting(roomDb)
        dao = SongDb(helper)
    }

    @After
    fun tearDown() {
        SongRoomDatabase.setForTesting(null)
        helper.close()
    }

    private fun songDoc(
        code: String,
        title: String = "Title $code",
        title_original: String? = "Original $code",
    ): SongDocument {
        val blocks = mutableListOf<Block>(
            PBlock(role = "title", content = Line.Plain(title)),
        )
        if (title_original != null) {
            blocks.add(PBlock(role = "title_original", content = Line.Plain(title_original)))
        }
        blocks.add(PBlock(role = "tune", content = Line.Plain("tune-$code")))
        blocks.add(
            RowBlock(
                items = listOf(
                    PBlock(role = "authors_lyric", content = Line.Plain("Author A; Author B")),
                    PBlock(role = "authors_music", content = Line.Plain("Composer C")),
                ),
            ),
        )
        blocks.add(ScriptureBlock(osis = "John.3.16"))
        blocks.add(PBlock(role = "musical", content = Line.Plain("C 4/4")))
        blocks.add(
            LyricBlock(
                caption = Line.Plain("Indonesian"),
                verses = listOf(
                    Verse(
                        kind = VerseKind.NORMAL,
                        lines = listOf(VerseLine.Simple(Line.Plain("Line 1")), VerseLine.Simple(Line.Plain("Line 2"))),
                    ),
                    Verse(kind = VerseKind.REFRAIN, lines = listOf(VerseLine.Simple(Line.Plain("Chorus")))),
                ),
            ),
        )
        return SongDocument(code = code, meta = SongDocumentJson.deriveMeta(blocks), blocks = blocks)
    }

    private fun bookInfo(name: String = "NKB", title: String? = "Buku", copyright: String? = "©") =
        SongBookUtil.SongBookInfo().apply {
            this.name = name
            this.title = title
            this.copyright = copyright
        }

    private fun store(bookName: String, vararg docs: SongDocument) {
        dao.storeSongs(bookName, docs.toList(), SongDocumentJson.DATA_FORMAT_VERSION)
    }

    @Test
    fun `storeSongs and getSong round-trip a full SongDocument byte-for-byte through the JSON BLOB`() {
        val s = songDoc("001", title = "Hymn 1")
        store("NKB", s)

        val loaded = dao.getSong("NKB", "001")
        assertEquals(s, loaded)
    }

    @Test
    fun `getSong returns null for an unknown (bookName, code) pair`() {
        assertNull(dao.getSong("NKB", "missing"))
    }

    @Test
    fun `songExists reports presence and absence correctly`() {
        assertFalse(dao.songExists("NKB", "001"))
        store("NKB", songDoc("001"))
        assertTrue(dao.songExists("NKB", "001"))
        assertFalse(dao.songExists("NKB", "002"))
    }

    @Test
    fun `storeSongs replaces every existing song for the (bookName, dataFormatVersion) tuple`() {
        dao.storeSongs("NKB", listOf(songDoc("001"), songDoc("002")), SongDocumentJson.DATA_FORMAT_VERSION)
        dao.storeSongs("NKB", listOf(songDoc("003")), SongDocumentJson.DATA_FORMAT_VERSION)

        assertNull(dao.getSong("NKB", "001"))
        assertNull(dao.getSong("NKB", "002"))
        assertNotNull(dao.getSong("NKB", "003"))
    }

    @Test
    fun `storeSongs preserves caller-supplied display ordering`() {
        dao.storeSongs("NKB", listOf(songDoc("A"), songDoc("B"), songDoc("C")), SongDocumentJson.DATA_FORMAT_VERSION)
        val codes = dao.listSongInfosByBookName("NKB").map { it.code }
        assertEquals(listOf("A", "B", "C"), codes)
    }

    @Test
    fun `getFirstSongFromBook returns the song with ordering = 1`() {
        dao.storeSongs("NKB", listOf(songDoc("A"), songDoc("B")), SongDocumentJson.DATA_FORMAT_VERSION)
        val first = dao.getFirstSongFromBook("NKB")
        assertNotNull(first)
        assertEquals("A", first!!.code)
    }

    @Test
    fun `getFirstSongFromBook returns null for an empty book`() {
        assertNull(dao.getFirstSongFromBook("NKB"))
    }

    @Test
    fun `getAnySong returns a pair of (bookName, doc) sorted by bookName then ordering`() {
        dao.storeSongs("PKJ", listOf(songDoc("P1")), SongDocumentJson.DATA_FORMAT_VERSION)
        dao.storeSongs("NKB", listOf(songDoc("N1")), SongDocumentJson.DATA_FORMAT_VERSION)
        val pair = dao.getAnySong()
        assertNotNull(pair)
        assertEquals("NKB", pair!!.first)
        assertEquals("N1", pair.second.code)
    }

    @Test
    fun `getAnySong returns null when no songs exist`() {
        assertNull(dao.getAnySong())
    }

    @Test
    fun `listSongInfosByBookName returns lightweight SongInfo records in display order`() {
        dao.storeSongs(
            "NKB",
            listOf(songDoc("A", title = "Alpha"), songDoc("B", title = "Bravo")),
            SongDocumentJson.DATA_FORMAT_VERSION,
        )
        val rows = dao.listSongInfosByBookName("NKB")
        assertEquals(listOf("A", "B"), rows.map { it.code })
        assertEquals(listOf("Alpha", "Bravo"), rows.map { it.title })
    }

    @Test
    fun `listSongInfosByBookName with null bookName lists every book in display order`() {
        dao.storeSongs("PKJ", listOf(songDoc("P1", title = "Papa")), SongDocumentJson.DATA_FORMAT_VERSION)
        dao.storeSongs(
            "NKB",
            listOf(songDoc("A", title = "Alpha"), songDoc("B", title = "Bravo")),
            SongDocumentJson.DATA_FORMAT_VERSION,
        )
        val rows = dao.listSongInfosByBookName(null)
        assertEquals(listOf("A", "B", "P1"), rows.map { it.code })
        assertEquals(listOf("NKB", "NKB", "PKJ"), rows.map { it.bookName })
    }

    @Test
    fun `listSongInfosByBookNameAndDeepFilter applies title-substring matching`() {
        dao.storeSongs(
            "NKB",
            listOf(
                songDoc("A", title = "Hosanna in the highest"),
                songDoc("B", title = "Amazing grace"),
                songDoc("C", title = "Holy holy holy"),
            ),
            SongDocumentJson.DATA_FORMAT_VERSION,
        )
        val rows = dao.listSongInfosByBookNameAndDeepFilter("NKB", "holy")
        // "Holy holy holy" matches; the others do not.
        assertEquals(listOf("C"), rows.map { it.code })
    }

    @Test
    fun `listSongInfosByBookNameAndDeepFilter with null bookName scans every book`() {
        dao.storeSongs("NKB", listOf(songDoc("A", title = "Hosanna")), SongDocumentJson.DATA_FORMAT_VERSION)
        dao.storeSongs("PKJ", listOf(songDoc("P", title = "Hosanna")), SongDocumentJson.DATA_FORMAT_VERSION)
        val rows = dao.listSongInfosByBookNameAndDeepFilter(null, "hosanna")
        assertEquals(2, rows.size)
    }

    @Test
    fun `deleteSongBook removes book metadata, every song, and vacuums without losing other books`() {
        dao.insertSongBookInfo(bookInfo(name = "NKB", title = "Buku NKB"))
        dao.insertSongBookInfo(bookInfo(name = "PKJ", title = "Buku PKJ"))
        dao.storeSongs("NKB", listOf(songDoc("001"), songDoc("002")), SongDocumentJson.DATA_FORMAT_VERSION)
        dao.storeSongs("PKJ", listOf(songDoc("P01")), SongDocumentJson.DATA_FORMAT_VERSION)

        val deleted = dao.deleteSongBook("NKB")
        assertEquals(2, deleted)
        assertNull(dao.getSong("NKB", "001"))
        assertNull(dao.getSongBookInfo("NKB"))
        // The unrelated book is fully intact: songs survive the VACUUM and the
        // book metadata is still readable.
        assertNotNull(dao.getSong("PKJ", "P01"))
        assertEquals("Buku PKJ", dao.getSongBookInfo("PKJ")?.title)
    }

    @Test
    fun `insertSongBookInfo and getSongBookInfo round-trip every field`() {
        dao.insertSongBookInfo(bookInfo(name = "NKB", title = "Title", copyright = "©NKB"))
        val info = dao.getSongBookInfo("NKB")
        assertNotNull(info)
        assertEquals("NKB", info!!.name)
        assertEquals("Title", info.title)
        assertEquals("©NKB", info.copyright)
    }

    @Test
    fun `insertSongBookInfo replaces an existing row with the same name`() {
        dao.insertSongBookInfo(bookInfo(name = "NKB", title = "v1"))
        dao.insertSongBookInfo(bookInfo(name = "NKB", title = "v2"))
        assertEquals("v2", dao.getSongBookInfo("NKB")?.title)
        // And there is no duplicate row left behind.
        assertEquals(1, dao.countSongBookInfos())
    }

    @Test
    fun `getSongBookInfo returns null when the row is missing`() {
        assertNull(dao.getSongBookInfo("missing"))
    }

    @Test
    fun `listSongBookInfos returns rows in name-ascending order`() {
        dao.insertSongBookInfo(bookInfo(name = "PKJ"))
        dao.insertSongBookInfo(bookInfo(name = "KJ"))
        dao.insertSongBookInfo(bookInfo(name = "NKB"))
        val rows = dao.listSongBookInfos()
        assertEquals(listOf("KJ", "NKB", "PKJ"), rows.map { it.name })
    }

    @Test
    fun `countSongBookInfos reports the table size`() {
        assertEquals(0, dao.countSongBookInfos())
        dao.insertSongBookInfo(bookInfo(name = "NKB"))
        dao.insertSongBookInfo(bookInfo(name = "PKJ"))
        assertEquals(2, dao.countSongBookInfos())
    }

    @Test
    fun `getDataFormatVersionForSongs returns 0 for empty book and the JSON version when present`() {
        assertEquals(0, dao.getDataFormatVersionForSongs("NKB"))
        store("NKB", songDoc("001"))
        assertEquals(SongDocumentJson.DATA_FORMAT_VERSION, dao.getDataFormatVersionForSongs("NKB"))
    }

    @Test
    fun `getSongUpdateTime returns 0 for missing and a positive value for present`() {
        assertEquals(0, dao.getSongUpdateTime("NKB", "missing"))
        store("NKB", songDoc("001"))
        assertTrue(dao.getSongUpdateTime("NKB", "001") > 0)
    }

    @Test
    fun `the stored BLOB is UTF-8 JSON matching SongDocumentJson encode`() {
        val s = songDoc("042")
        store("NKB", s)

        val row = SongRoomDatabase.get(RuntimeEnvironment.getApplication()).songRoomDao().findSongInfoByBookNameAndCode("NKB", "042")
        assertNotNull(row)
        assertEquals(SongDocumentJson.DATA_FORMAT_VERSION, row!!.dataFormatVersion)
        val text = String(row.data!!, Charsets.UTF_8)
        assertEquals(SongDocumentJson.decode(text), SongDocumentJson.decode(SongDocumentJson.encode(s)))
    }

    @Test
    fun `getSong lazily converts a legacy Parcelable row to JSON and bumps dataFormatVersion to 5, idempotently`() {
        val legacySong = Song().apply {
            code = "L1"
            title = "Legacy Song"
            title_original = null
            authors_lyric = mutableListOf()
            authors_music = mutableListOf()
            tune = null
            keySignature = null
            timeSignature = null
            lyrics = mutableListOf()
            scriptureReferences = null
        }
        val p = android.os.Parcel.obtain()
        legacySong.writeToParcelCompat(3, p, 0)
        val bytes = p.marshall()
        p.recycle()

        val roomDao = SongRoomDatabase.get(RuntimeEnvironment.getApplication()).songRoomDao()
        roomDao.insertSongInfo(SongInfoEntity(0L, "NKB", "L1", "Legacy Song", null, 1, 3, bytes, yuku.alkitab.base.util.Sqlitil.nowDateTime()))

        val doc = dao.getSong("NKB", "L1")
        assertNotNull(doc)
        assertEquals("L1", doc!!.code)
        assertEquals("Legacy Song", doc.meta.title)

        // The write-back runs on a background thread (Background.run) so the row doesn't flip to
        // dataFormatVersion 5 synchronously with the read that triggered it; poll for it instead of
        // asserting immediately.
        awaitDataFormatVersion("NKB", SongDocumentJson.DATA_FORMAT_VERSION)

        // idempotent: a second read returns the same document (now via the JSON path).
        val doc2 = dao.getSong("NKB", "L1")
        assertEquals(doc, doc2)
    }

    private fun awaitDataFormatVersion(bookName: String, expected: Int, timeoutMs: Long = 2000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (dao.getDataFormatVersionForSongs(bookName) != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertEquals(expected, dao.getDataFormatVersionForSongs(bookName))
    }
}
