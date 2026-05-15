package yuku.alkitab.base.storage.room

import android.app.Application
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertArrayEquals
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

/**
 * Tests for the Room-generated [SongRoomDao]. Verifies the two
 * Songs-subsystem tables — `song_info` and `song_book_info` — and the
 * transactional composites that mirror the legacy facade's
 * `beginTransactionNonExclusive` semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SongRoomDaoTest {
    private lateinit var db: SongRoomDatabase
    private lateinit var dao: SongRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, SongRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.songRoomDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun songInfo(
        bookName: String? = "NKB",
        code: String? = "001",
        title: String? = "Hymn 1",
        title_original: String? = "Original 1",
        ordering: Int = 1,
        dataFormatVersion: Int = 3,
        data: ByteArray? = byteArrayOf(1, 2, 3),
        updateTime: Int = 1_700_000_000,
    ) = SongInfoEntity(
        _id = 0L,
        bookName = bookName,
        code = code,
        title = title,
        title_original = title_original,
        ordering = ordering,
        dataFormatVersion = dataFormatVersion,
        data = data,
        updateTime = updateTime,
    )

    private fun songBookInfo(
        name: String? = "NKB",
        title: String? = "Nyanyikanlah Kidung Baru",
        copyright: String? = "© Yamuger",
    ) = SongBookInfoEntity(
        _id = 0L,
        name = name,
        title = title,
        copyright = copyright,
    )

    @Test
    fun `insertSongInfo assigns a positive _id and round-trips every column`() {
        val id = dao.insertSongInfo(songInfo(code = "042", data = byteArrayOf(10, 20, 30)))
        assertTrue(id > 0)
        val loaded = dao.listAllSongInfos().single()
        assertEquals("NKB", loaded.bookName)
        assertEquals("042", loaded.code)
        assertEquals("Hymn 1", loaded.title)
        assertEquals("Original 1", loaded.title_original)
        assertEquals(1, loaded.ordering)
        assertEquals(3, loaded.dataFormatVersion)
        assertArrayEquals(byteArrayOf(10, 20, 30), loaded.data)
        assertEquals(1_700_000_000, loaded.updateTime)
    }

    @Test
    fun `findSongInfoByBookNameAndCode returns null when no row matches`() {
        assertNull(dao.findSongInfoByBookNameAndCode("NKB", "missing"))
    }

    @Test
    fun `findSongInfoByBookNameAndCode returns the row when present`() {
        dao.insertSongInfo(songInfo(code = "007"))
        val row = dao.findSongInfoByBookNameAndCode("NKB", "007")
        assertNotNull(row)
        assertEquals("007", row!!.code)
    }

    @Test
    fun `countSongInfosByBookNameAndCode returns 0 for missing and 1 for present`() {
        assertEquals(0, dao.countSongInfosByBookNameAndCode("NKB", "missing"))
        dao.insertSongInfo(songInfo(code = "003"))
        assertEquals(1, dao.countSongInfosByBookNameAndCode("NKB", "003"))
    }

    @Test
    fun `findFirstSongInfoByBookName returns the lowest-ordering row`() {
        dao.insertSongInfo(songInfo(code = "B", ordering = 5))
        dao.insertSongInfo(songInfo(code = "A", ordering = 1))
        dao.insertSongInfo(songInfo(code = "C", ordering = 9))
        val first = dao.findFirstSongInfoByBookName("NKB")
        assertNotNull(first)
        assertEquals("A", first!!.code)
        assertEquals(1, first.ordering)
    }

    @Test
    fun `findFirstSongInfoByBookName returns null for empty book`() {
        assertNull(dao.findFirstSongInfoByBookName("NKB"))
    }

    @Test
    fun `findAnySongInfo returns first by bookName then ordering`() {
        dao.insertSongInfo(songInfo(bookName = "PKJ", code = "001", ordering = 1))
        dao.insertSongInfo(songInfo(bookName = "NKB", code = "099", ordering = 99))
        dao.insertSongInfo(songInfo(bookName = "NKB", code = "001", ordering = 1))
        val any = dao.findAnySongInfo()
        assertNotNull(any)
        assertEquals("NKB", any!!.bookName)
        assertEquals("001", any.code)
    }

    @Test
    fun `findAnySongInfo returns null when no rows exist`() {
        assertNull(dao.findAnySongInfo())
    }

    @Test
    fun `listSongInfosByBookName orders by ordering ASC`() {
        dao.insertSongInfo(songInfo(code = "C", ordering = 3))
        dao.insertSongInfo(songInfo(code = "A", ordering = 1))
        dao.insertSongInfo(songInfo(code = "B", ordering = 2))
        val rows = dao.listSongInfosByBookName("NKB")
        assertEquals(listOf("A", "B", "C"), rows.map { it.code })
    }

    @Test
    fun `listSongInfosByBookName returns empty when no rows match`() {
        dao.insertSongInfo(songInfo(bookName = "PKJ", code = "001"))
        assertTrue(dao.listSongInfosByBookName("NKB").isEmpty())
    }

    @Test
    fun `listAllSongInfos orders by bookName ASC then ordering ASC`() {
        dao.insertSongInfo(songInfo(bookName = "PKJ", code = "P1", ordering = 1))
        dao.insertSongInfo(songInfo(bookName = "NKB", code = "N2", ordering = 2))
        dao.insertSongInfo(songInfo(bookName = "NKB", code = "N1", ordering = 1))
        val rows = dao.listAllSongInfos()
        assertEquals(listOf("N1", "N2", "P1"), rows.map { it.code })
    }

    @Test
    fun `findDataFormatVersionForBookName returns null for empty book and the dataFormatVersion for present`() {
        assertNull(dao.findDataFormatVersionForBookName("NKB"))
        dao.insertSongInfo(songInfo(dataFormatVersion = 3))
        assertEquals(3, dao.findDataFormatVersionForBookName("NKB"))
    }

    @Test
    fun `findUpdateTimeByBookNameAndCode returns null for missing and the updateTime for present`() {
        assertNull(dao.findUpdateTimeByBookNameAndCode("NKB", "missing"))
        dao.insertSongInfo(songInfo(code = "001", updateTime = 1_700_001_234))
        assertEquals(1_700_001_234, dao.findUpdateTimeByBookNameAndCode("NKB", "001"))
    }

    @Test
    fun `replaceSongsForBookNameAndDataFormatVersion removes existing rows for the (bookName, dfv) tuple and inserts the new list`() {
        dao.insertSongInfo(songInfo(code = "old1", dataFormatVersion = 3, ordering = 1))
        dao.insertSongInfo(songInfo(code = "old2", dataFormatVersion = 3, ordering = 2))
        dao.insertSongInfo(songInfo(code = "keep", dataFormatVersion = 2, ordering = 1))

        dao.replaceSongsForBookNameAndDataFormatVersion(
            bookName = "NKB",
            dataFormatVersion = 3,
            entities = listOf(
                songInfo(code = "new1", dataFormatVersion = 3, ordering = 1),
                songInfo(code = "new2", dataFormatVersion = 3, ordering = 2),
            ),
        )

        val codes = dao.listSongInfosByBookName("NKB").map { it.code }.toSet()
        assertEquals(
            "old rows at the matching dfv should be gone, the dfv=2 row should survive, and the two new rows should be present",
            setOf("new1", "new2", "keep"),
            codes,
        )
    }

    @Test
    fun `replaceSongsForBookNameAndDataFormatVersion is a clean wipe when passed an empty list`() {
        dao.insertSongInfo(songInfo(code = "old1", dataFormatVersion = 3))
        dao.replaceSongsForBookNameAndDataFormatVersion("NKB", 3, emptyList())
        assertTrue(dao.listSongInfosByBookName("NKB").isEmpty())
    }

    @Test
    fun `data column round-trips null and an empty ByteArray`() {
        dao.insertSongInfo(songInfo(code = "n", data = null))
        dao.insertSongInfo(songInfo(code = "e", data = byteArrayOf()))
        val rows = dao.listAllSongInfos().associateBy { it.code }
        assertNull(rows.getValue("n").data)
        assertArrayEquals(byteArrayOf(), rows.getValue("e").data)
    }

    @Test
    fun `insertSongBookInfo assigns a positive _id and round-trips every column`() {
        val id = dao.insertSongBookInfo(songBookInfo(name = "NKB", title = "Buku", copyright = "©"))
        assertTrue(id > 0)
        val rows = dao.listAllSongBookInfos()
        assertEquals(1, rows.size)
        assertEquals("NKB", rows.single().name)
        assertEquals("Buku", rows.single().title)
        assertEquals("©", rows.single().copyright)
    }

    @Test
    fun `findSongBookInfoByName returns null for missing and the row when present`() {
        assertNull(dao.findSongBookInfoByName("missing"))
        dao.insertSongBookInfo(songBookInfo(name = "NKB"))
        assertNotNull(dao.findSongBookInfoByName("NKB"))
    }

    @Test
    fun `listAllSongBookInfos orders by name ASC`() {
        dao.insertSongBookInfo(songBookInfo(name = "PKJ"))
        dao.insertSongBookInfo(songBookInfo(name = "NKB"))
        dao.insertSongBookInfo(songBookInfo(name = "KJ"))
        assertEquals(listOf("KJ", "NKB", "PKJ"), dao.listAllSongBookInfos().map { it.name })
    }

    @Test
    fun `insertOrReplaceSongBookInfo inserts when absent and replaces in place when present`() {
        dao.insertOrReplaceSongBookInfo("NKB", "v1", "©v1")
        dao.insertOrReplaceSongBookInfo("NKB", "v2", "©v2")
        val rows = dao.listAllSongBookInfos()
        assertEquals("no duplicate row should be created on update", 1, rows.size)
        assertEquals("v2", rows.single().title)
        assertEquals("©v2", rows.single().copyright)
    }

    @Test
    fun `deleteSongBookAndSongs removes both the book metadata and every song in it and returns the song count`() {
        dao.insertSongBookInfo(songBookInfo(name = "NKB"))
        dao.insertSongBookInfo(songBookInfo(name = "PKJ"))
        dao.insertSongInfo(songInfo(bookName = "NKB", code = "001"))
        dao.insertSongInfo(songInfo(bookName = "NKB", code = "002"))
        dao.insertSongInfo(songInfo(bookName = "PKJ", code = "001"))

        val deleted = dao.deleteSongBookAndSongs("NKB")
        assertEquals(2, deleted)
        assertNull(dao.findSongBookInfoByName("NKB"))
        assertEquals(listOf("PKJ"), dao.listAllSongBookInfos().map { it.name })
        assertTrue(dao.listSongInfosByBookName("NKB").isEmpty())
        assertEquals(1, dao.listSongInfosByBookName("PKJ").size)
    }

    @Test
    fun `deleteSongInfosByBookName returns the affected count`() {
        dao.insertSongInfo(songInfo(bookName = "NKB", code = "001"))
        dao.insertSongInfo(songInfo(bookName = "NKB", code = "002"))
        dao.insertSongInfo(songInfo(bookName = "PKJ", code = "001"))
        assertEquals(2, dao.deleteSongInfosByBookName("NKB"))
        assertEquals(1, dao.listAllSongInfos().size)
    }

    @Test
    fun `countAllSongInfos and countAllSongBookInfos report the table size`() {
        assertEquals(0, dao.countAllSongInfos())
        assertEquals(0, dao.countAllSongBookInfos())
        dao.insertSongInfo(songInfo(code = "001"))
        dao.insertSongBookInfo(songBookInfo(name = "NKB"))
        assertEquals(1, dao.countAllSongInfos())
        assertEquals(1, dao.countAllSongBookInfos())
    }
}
