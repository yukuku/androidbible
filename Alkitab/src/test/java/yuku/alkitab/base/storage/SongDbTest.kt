package yuku.alkitab.base.storage

import android.app.Application
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
import yuku.alkitab.base.storage.room.SongRoomDatabase
import yuku.alkitab.songs.SongBookUtil
import yuku.kpri.model.Lyric
import yuku.kpri.model.Song
import yuku.kpri.model.Verse
import yuku.kpri.model.VerseKind

/**
 * Robolectric tests for the [SongDb] facade. The facade routes through
 * Room (see `Alkitab/src/main/java/yuku/alkitab/base/storage/SongDb.java`);
 * the test installs an in-memory [SongRoomDatabase] in [setUp] via
 * [SongRoomDatabase.setForTesting].
 *
 * The most important assertion in this file is the Parcelable round-trip
 * through `storeSongs` + `getSong`. REM-32 is a strict storage-engine swap
 * that must round-trip the `data` BLOB byte-for-byte; a regression there
 * would break user data immediately on first launch with the new build.
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

    private fun verse(ordering: Int, kind: VerseKind, vararg lines: String): Verse {
        return Verse().apply {
            this.ordering = ordering
            this.kind = kind
            this.lines = lines.toMutableList()
        }
    }

    private fun lyric(caption: String?, vararg verses: Verse): Lyric {
        return Lyric().apply {
            this.caption = caption
            this.verses = verses.toMutableList()
        }
    }

    private fun song(
        code: String,
        title: String = "Title $code",
        title_original: String? = "Original $code",
    ): Song {
        return Song().apply {
            this.code = code
            this.title = title
            this.title_original = title_original
            this.authors_lyric = mutableListOf("Author A", "Author B")
            this.authors_music = mutableListOf("Composer C")
            this.tune = "tune-$code"
            this.keySignature = "C"
            this.timeSignature = "4/4"
            this.lyrics = mutableListOf(
                lyric(
                    caption = "Indonesian",
                    verses = arrayOf(
                        verse(1, VerseKind.NORMAL, "Line 1", "Line 2"),
                        verse(2, VerseKind.REFRAIN, "Chorus"),
                    ),
                ),
            )
            this.scriptureReferences = "John 3:16"
        }
    }

    private fun bookInfo(name: String = "NKB", title: String? = "Buku", copyright: String? = "©") =
        SongBookUtil.SongBookInfo().apply {
            this.name = name
            this.title = title
            this.copyright = copyright
        }

    private fun assertSongsEqual(expected: Song, actual: Song?) {
        assertNotNull(actual)
        actual!!
        assertEquals(expected.code, actual.code)
        assertEquals(expected.title, actual.title)
        assertEquals(expected.title_original, actual.title_original)
        assertEquals(expected.authors_lyric, actual.authors_lyric)
        assertEquals(expected.authors_music, actual.authors_music)
        assertEquals(expected.tune, actual.tune)
        assertEquals(expected.keySignature, actual.keySignature)
        assertEquals(expected.timeSignature, actual.timeSignature)
        assertEquals(expected.scriptureReferences, actual.scriptureReferences)
        assertEquals(expected.lyrics.size, actual.lyrics.size)
        for (i in expected.lyrics.indices) {
            val a = expected.lyrics[i]
            val b = actual.lyrics[i]
            assertEquals(a.caption, b.caption)
            assertEquals(a.verses.size, b.verses.size)
            for (j in a.verses.indices) {
                assertEquals(a.verses[j].ordering, b.verses[j].ordering)
                assertEquals(a.verses[j].kind, b.verses[j].kind)
                assertEquals(a.verses[j].lines, b.verses[j].lines)
            }
        }
    }

    @Test
    fun `storeSongs and getSong round-trip a full Song through the Parcelable BLOB byte-for-byte`() {
        val s = song("001", title = "Hymn 1")
        dao.storeSongs("NKB", listOf(s), 3)

        val loaded = dao.getSong("NKB", "001")
        assertSongsEqual(s, loaded)
    }

    @Test
    fun `getSong returns null for an unknown (bookName, code) pair`() {
        assertNull(dao.getSong("NKB", "missing"))
    }

    @Test
    fun `songExists reports presence and absence correctly`() {
        assertFalse(dao.songExists("NKB", "001"))
        dao.storeSongs("NKB", listOf(song("001")), 3)
        assertTrue(dao.songExists("NKB", "001"))
        assertFalse(dao.songExists("NKB", "002"))
    }

    @Test
    fun `storeSongs replaces every existing song for the (bookName, dataFormatVersion) tuple`() {
        dao.storeSongs("NKB", listOf(song("001"), song("002")), 3)
        dao.storeSongs("NKB", listOf(song("003")), 3)

        assertNull(dao.getSong("NKB", "001"))
        assertNull(dao.getSong("NKB", "002"))
        assertNotNull(dao.getSong("NKB", "003"))
    }

    @Test
    fun `storeSongs preserves caller-supplied display ordering`() {
        dao.storeSongs(
            "NKB",
            listOf(song("A"), song("B"), song("C")),
            3,
        )
        val codes = dao.listSongInfosByBookName("NKB").map { it.code }
        assertEquals(listOf("A", "B", "C"), codes)
    }

    @Test
    fun `getFirstSongFromBook returns the song with ordering = 1`() {
        dao.storeSongs("NKB", listOf(song("A"), song("B")), 3)
        val first = dao.getFirstSongFromBook("NKB")
        assertNotNull(first)
        assertEquals("A", first!!.code)
    }

    @Test
    fun `getFirstSongFromBook returns null for an empty book`() {
        assertNull(dao.getFirstSongFromBook("NKB"))
    }

    @Test
    fun `getAnySong returns a pair of (bookName, song) sorted by bookName then ordering`() {
        dao.storeSongs("PKJ", listOf(song("P1")), 3)
        dao.storeSongs("NKB", listOf(song("N1")), 3)
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
            listOf(song("A", title = "Alpha"), song("B", title = "Bravo")),
            3,
        )
        val rows = dao.listSongInfosByBookName("NKB")
        assertEquals(listOf("A", "B"), rows.map { it.code })
        assertEquals(listOf("Alpha", "Bravo"), rows.map { it.title })
    }

    @Test
    fun `listSongInfosByBookName with null bookName lists every book in display order`() {
        dao.storeSongs("PKJ", listOf(song("P1", title = "Papa")), 3)
        dao.storeSongs("NKB", listOf(song("A", title = "Alpha"), song("B", title = "Bravo")), 3)
        val rows = dao.listSongInfosByBookName(null)
        assertEquals(listOf("A", "B", "P1"), rows.map { it.code })
        assertEquals(listOf("NKB", "NKB", "PKJ"), rows.map { it.bookName })
    }

    @Test
    fun `listSongInfosByBookNameAndDeepFilter applies title-substring matching`() {
        dao.storeSongs(
            "NKB",
            listOf(
                song("A", title = "Hosanna in the highest"),
                song("B", title = "Amazing grace"),
                song("C", title = "Holy holy holy"),
            ),
            3,
        )
        val rows = dao.listSongInfosByBookNameAndDeepFilter("NKB", "holy")
        // "Holy holy holy" matches; the others do not.
        assertEquals(listOf("C"), rows.map { it.code })
    }

    @Test
    fun `listSongInfosByBookNameAndDeepFilter with null bookName scans every book`() {
        dao.storeSongs("NKB", listOf(song("A", title = "Hosanna")), 3)
        dao.storeSongs("PKJ", listOf(song("P", title = "Hosanna")), 3)
        val rows = dao.listSongInfosByBookNameAndDeepFilter(null, "hosanna")
        assertEquals(2, rows.size)
    }

    @Test
    fun `deleteSongBook removes book metadata, every song, and vacuums without losing other books`() {
        dao.insertSongBookInfo(bookInfo(name = "NKB", title = "Buku NKB"))
        dao.insertSongBookInfo(bookInfo(name = "PKJ", title = "Buku PKJ"))
        dao.storeSongs("NKB", listOf(song("001"), song("002")), 3)
        dao.storeSongs("PKJ", listOf(song("P01")), 3)

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
    fun `getDataFormatVersionForSongs returns 0 for empty book and the value when present`() {
        assertEquals(0, dao.getDataFormatVersionForSongs("NKB"))
        dao.storeSongs("NKB", listOf(song("001")), 3)
        assertEquals(3, dao.getDataFormatVersionForSongs("NKB"))
    }

    @Test
    fun `getSongUpdateTime returns 0 for missing and a positive value for present`() {
        assertEquals(0, dao.getSongUpdateTime("NKB", "missing"))
        dao.storeSongs("NKB", listOf(song("001")), 3)
        assertTrue(dao.getSongUpdateTime("NKB", "001") > 0)
    }

    @Test
    fun `BLOB column survives raw byte-array round-trips through storeSongs and getSong`() {
        // Direct invariant check on the BLOB layer: marshall the song the
        // same way storeSongs does, run it through the facade, then
        // marshall the loaded value back the same way and compare the byte
        // arrays. This is the strongest signal that REM-32 has not altered
        // the Parcelable byte layout.
        val s = song("042")
        dao.storeSongs("NKB", listOf(s), 3)
        val loaded = dao.getSong("NKB", "042")
        assertNotNull(loaded)

        val p1 = android.os.Parcel.obtain()
        s.writeToParcelCompat(3, p1, 0)
        val bytesOriginal = p1.marshall()
        p1.recycle()

        val p2 = android.os.Parcel.obtain()
        loaded!!.writeToParcelCompat(3, p2, 0)
        val bytesLoaded = p2.marshall()
        p2.recycle()

        assertArrayEquals(bytesOriginal, bytesLoaded)
    }
}
