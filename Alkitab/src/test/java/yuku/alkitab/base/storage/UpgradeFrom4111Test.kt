package yuku.alkitab.base.storage

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.preference.PreferenceManager
import androidx.room.Room
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.storage.room.SongDbDataMigration
import yuku.alkitab.base.storage.room.SongRoomDatabase
import yuku.alkitab.songs.newdoc.AospParcelWriter
import yuku.alkitab.songs.newdoc.LyricBlock
import yuku.alkitab.songs.newdoc.ParcelLayout
import yuku.alkitab.songs.newdoc.SongDocumentJson
import yuku.alkitab.songs.newdoc.plainText
import yuku.kpri.model.Lyric
import yuku.kpri.model.Song
import yuku.kpri.model.Verse
import yuku.kpri.model.VerseKind

/**
 * End-to-end upgrade tests for the one path every existing user actually
 * takes into 5.0.0: the last published non-beta release, 4.11.1
 * (`versionCode` [VERSION_CODE_4_11_1]), upgrading to the current build.
 *
 * The other migration suites in this package seed `oldVersion` values from
 * the 3.x/4.0 era, where the `onUpgrade` branches do convert data. 4.11.1 is
 * the opposite case and needs its own coverage: every branch threshold in
 * [InternalDbHelper.onUpgrade] is at or below 14000265, so upgrading from
 * 17000531 must run *no* branch at all. That makes the interesting assertion
 * not "did the migration transform the data correctly" but "does the app's
 * current read path work against an untouched 4.11.1 file" — which is only
 * true as long as nothing added a table, column, or index without a matching
 * upgrade branch.
 *
 * [`upgrade from 4 11 1 produces the same schema as a fresh install, for both databases`]
 * pins exactly that invariant for both SQLite files: the schema reached by
 * upgrading from 4.11.1 must equal the schema `onCreate` gives a fresh
 * install. It fails in the dangerous direction — someone extends `onCreate`
 * (or a [Table] enum, which is DDL-generating) and forgets the upgrade
 * branch, leaving upgraders with a column the code expects and the database
 * lacks.
 *
 * The Songs half covers the storage seam for REM-32 and REM-21 together: a
 * 4.11.1 install stored songs as `dataFormatVersion` 3 Parcelable blobs in
 * the legacy `SongDb` file, and the current build has to copy those rows into
 * Room and then decode them as JSON-or-legacy on read. The blob is written
 * with [AospParcelWriter] rather than Robolectric's `Parcel`, whose shadow
 * does not use the native binary layout a real device wrote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class UpgradeFrom4111Test {
    private lateinit var app: Application
    private lateinit var alkitabDbFile: File
    private lateinit var songDbFile: File

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit()
        Preferences.invalidate()
        alkitabDbFile = app.getDatabasePath("AlkitabDb")
        songDbFile = app.getDatabasePath("SongDb")
        alkitabDbFile.parentFile?.mkdirs()
        deleteDbFiles()
    }

    @After
    fun tearDown() {
        SongRoomDatabase.setForTesting(null)
        deleteDbFiles()
    }

    private fun deleteDbFiles() {
        SQLiteDatabase.deleteDatabase(alkitabDbFile)
        SQLiteDatabase.deleteDatabase(songDbFile)
    }

    // `SQLiteOpenHelper` has `close()` but does not implement `AutoCloseable`,
    // so `use` is unavailable on it.
    private fun <T> withInternalHelper(block: (InternalDbHelper) -> T): T {
        val helper = InternalDbHelper(app)
        try {
            return block(helper)
        } finally {
            helper.close()
        }
    }

    private fun <T> withSongHelper(block: (SongDbHelper) -> T): T {
        val helper = SongDbHelper()
        try {
            return block(helper)
        } finally {
            helper.close()
        }
    }

    // region Schema snapshotting

    /**
     * Every `CREATE` statement in the file, normalised for comparison:
     * `sqlite_master.sql` is stored as written, so two sides only match if
     * the DDL text matches. Autoindexes and `sqlite_sequence` are skipped —
     * SQLite synthesises those, and their presence tracks inserted rows
     * rather than declared schema.
     */
    private fun schemaOf(db: SQLiteDatabase): List<String> {
        val res = mutableListOf<String>()
        db.rawQuery(
            "select type, name, sql from sqlite_master " +
                "where name not like 'sqlite_%' and name != 'android_metadata' " +
                "and sql is not null order by type, name",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val sql = c.getString(2).replace(Regex("\\s+"), " ").trim()
                res.add("${c.getString(0)} ${c.getString(1)}: $sql")
            }
        }
        return res
    }

    /** `pragma table_info` for every table, so a column added without new DDL text is still caught. */
    private fun columnsOf(db: SQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        db.rawQuery(
            "select name from sqlite_master where type='table' and name not like 'sqlite_%' " +
                "and name != 'android_metadata' order by name",
            null,
        ).use { c -> while (c.moveToNext()) tables.add(c.getString(0)) }

        val res = mutableListOf<String>()
        for (t in tables) {
            db.rawQuery("pragma table_info($t)", null).use { c ->
                while (c.moveToNext()) {
                    res.add("$t.${c.getString(1)} ${c.getString(2)} notnull=${c.getInt(3)}")
                }
            }
        }
        return res.sorted()
    }

    // endregion

    /**
     * Produce a database file in the state 4.11.1 left it, seeded by [seed]
     * and stamped down to [VERSION_CODE_4_11_1].
     *
     * The schema comes from the frozen [ALKITAB_DDL_4_11_1] fixture, not from
     * running the current [InternalDbHelper.onCreate]. That distinction is
     * what gives
     * [`upgrade from 4 11 1 produces the same schema as a fresh install, for both databases`]
     * its teeth: deriving the "old" schema from today's `onCreate` would make
     * both sides of that comparison move together, and a column added without
     * an upgrade branch would pass.
     */
    private fun seedAlkitabDbAt4111(seed: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(alkitabDbFile, null).use { db ->
            ALKITAB_DDL_4_11_1.forEach { db.execSQL(it) }
            // 4.11.1's onCreate seeded five progress marks at ari 0.
            for (i in 0 until 5) {
                db.insert(Db.TABLE_ProgressMark, null, ContentValues().apply {
                    put(Db.ProgressMark.ari, 0)
                    put(Db.ProgressMark.preset_id, i)
                })
            }
            seed(db)
            db.version = VERSION_CODE_4_11_1
        }
    }

    private fun seedSongDbAt4111(seed: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(songDbFile, null).use { db ->
            SONG_DDL_4_11_1.forEach { db.execSQL(it) }
            seed(db)
            db.version = VERSION_CODE_4_11_1
        }
    }

    // region AlkitabDb

    @Test
    fun `upgrading AlkitabDb from 4 11 1 runs no migration branch and keeps every user row intact`() {
        seedAlkitabDbAt4111 { db ->
            db.insert(Db.TABLE_Marker, null, ContentValues().apply {
                put(Db.Marker.gid, "marker-gid-1")
                put(Db.Marker.ari, 0x01_02_03)
                put(Db.Marker.kind, 1)
                put(Db.Marker.caption, "a bookmark from 4.11.1")
                put(Db.Marker.verseCount, 1)
                put(Db.Marker.createTime, 1_600_000_000)
                put(Db.Marker.modifyTime, 1_600_000_001)
            })
            db.insert(Db.TABLE_Label, null, ContentValues().apply {
                put(Db.Label.gid, "label-gid-1")
                put(Db.Label.title, "Favourites")
                put(Db.Label.ordering, 1)
                put(Db.Label.backgroundColor, "#ff0000")
            })
            db.insert(Db.TABLE_Marker_Label, null, ContentValues().apply {
                put(Db.Marker_Label.gid, "ml-gid-1")
                put(Db.Marker_Label.marker_gid, "marker-gid-1")
                put(Db.Marker_Label.label_gid, "label-gid-1")
            })
            db.insert(Db.TABLE_Version, null, ContentValues().apply {
                put(Db.Version.locale, "in")
                put(Db.Version.shortName, "TB")
                put(Db.Version.longName, "Terjemahan Baru")
                put(Db.Version.filename, "/data/user/0/x/files/TB.yes")
                put(Db.Version.preset_name, "TB")
                put(Db.Version.active, 1)
                put(Db.Version.ordering, 2)
            })
            db.insert(Db.TABLE_ReadingPlan, null, ContentValues().apply {
                put(Db.ReadingPlan.version, 1)
                put(Db.ReadingPlan.name, "plan-a")
                put(Db.ReadingPlan.title, "Plan A")
                put(Db.ReadingPlan.duration, 365)
                put(Db.ReadingPlan.startTime, 1_600_000_000)
                put(Db.ReadingPlan.data, byteArrayOf(1, 2, 3))
            })
            db.insert(Db.TABLE_ReadingPlanProgress, null, ContentValues().apply {
                put(Db.ReadingPlanProgress.reading_plan_progress_gid, "rp-gid-1")
                put(Db.ReadingPlanProgress.reading_code, 7)
                put(Db.ReadingPlanProgress.checkTime, 1_600_000_500)
            })
            db.insert(Table.PerVersion.tableName(), null, ContentValues().apply {
                put(Table.PerVersion.versionId.name, "preset/TB")
            })
            // A progress mark the user actually moved (onCreate seeds five at ari 0).
            db.update(
                Db.TABLE_ProgressMark,
                ContentValues().apply {
                    put(Db.ProgressMark.ari, 0x02_03_04)
                    put(Db.ProgressMark.caption, "where I left off")
                    put(Db.ProgressMark.modifyTime, 1_600_000_900)
                },
                "${Db.ProgressMark.preset_id} = 0",
                null,
            )
        }

        withInternalHelper { helper ->
            val db = helper.writableDatabase

            // onUpgrade ran (the stamp is lifted to the current build) but no
            // branch inside it fired, so the seeded rows are untouched.
            assertEquals(App.getVersionCode(), db.version)

            assertEquals(1, countOf(db, Db.TABLE_Marker))
            assertEquals(1, countOf(db, Db.TABLE_Label))
            assertEquals(1, countOf(db, Db.TABLE_Marker_Label))
            assertEquals(1, countOf(db, Db.TABLE_Version))
            assertEquals(1, countOf(db, Db.TABLE_ReadingPlan))
            assertEquals(1, countOf(db, Db.TABLE_ReadingPlanProgress))
            assertEquals(1, countOf(db, Table.PerVersion.tableName()))
            assertEquals(5, countOf(db, Db.TABLE_ProgressMark))

            db.rawQuery(
                "select ${Db.Marker.gid}, ${Db.Marker.ari}, ${Db.Marker.caption} from ${Db.TABLE_Marker}",
                null,
            ).use { c ->
                assertEquals(true, c.moveToNext())
                assertEquals("marker-gid-1", c.getString(0))
                assertEquals(0x01_02_03, c.getInt(1))
                assertEquals("a bookmark from 4.11.1", c.getString(2))
            }
            db.rawQuery(
                "select ${Db.ProgressMark.ari}, ${Db.ProgressMark.caption} from ${Db.TABLE_ProgressMark} " +
                    "where ${Db.ProgressMark.preset_id} = 0",
                null,
            ).use { c ->
                assertEquals(true, c.moveToNext())
                assertEquals(0x02_03_04, c.getInt(0))
                assertEquals("where I left off", c.getString(1))
            }

            // The `Edisi` scaffold only exists to feed the 14000166 branch. A
            // 4.11.1 upgrade must not resurrect it.
            assertEquals(0, countOf(db, "sqlite_master", "type='table' and name='Edisi'"))
        }
    }

    @Test
    fun `the current read path resolves markers, labels, and versions from an upgraded 4 11 1 file`() {
        seedAlkitabDbAt4111 { db ->
            db.insert(Db.TABLE_Marker, null, ContentValues().apply {
                put(Db.Marker.gid, "marker-gid-1")
                put(Db.Marker.ari, 0x01_02_03)
                put(Db.Marker.kind, 1)
                put(Db.Marker.caption, "kept")
                put(Db.Marker.verseCount, 1)
                put(Db.Marker.createTime, 1_600_000_000)
                put(Db.Marker.modifyTime, 1_600_000_001)
            })
            db.insert(Db.TABLE_Label, null, ContentValues().apply {
                put(Db.Label.gid, "label-gid-1")
                put(Db.Label.title, "Favourites")
                put(Db.Label.ordering, 1)
                put(Db.Label.backgroundColor, "#ff0000")
            })
            db.insert(Db.TABLE_Marker_Label, null, ContentValues().apply {
                put(Db.Marker_Label.gid, "ml-gid-1")
                put(Db.Marker_Label.marker_gid, "marker-gid-1")
                put(Db.Marker_Label.label_gid, "label-gid-1")
            })
            db.insert(Db.TABLE_Version, null, ContentValues().apply {
                put(Db.Version.locale, "in")
                put(Db.Version.shortName, "TB")
                put(Db.Version.longName, "Terjemahan Baru")
                put(Db.Version.filename, "/data/user/0/x/files/TB.yes")
                put(Db.Version.preset_name, "TB")
                put(Db.Version.active, 1)
                put(Db.Version.ordering, 2)
            })
        }

        // Exercising the DAO layer (rather than raw SQL) is the point: this is
        // the code that was rewritten after 4.11.1, so it has to be proven
        // against a 4.11.1-shaped file, not only against a fresh one.
        withInternalHelper { helper ->
            val internalDb = InternalDb(helper)

            val marker = internalDb.getMarkerByGid("marker-gid-1")
            assertNotNull(marker)
            assertEquals(0x01_02_03, marker!!.ari)
            assertEquals("kept", marker.caption)

            val label = internalDb.getLabelByGid("label-gid-1")
            assertNotNull(label)
            assertEquals("Favourites", label!!.title)

            assertEquals(listOf("Favourites"), internalDb.listLabelsByMarker(marker).map { it.title })

            assertEquals(listOf("TB"), internalDb.listAllVersions().map { it.shortName })
        }
    }

    // endregion

    // region Songs (REM-32 storage swap + REM-21 payload swap)

    @Test
    fun `a song stored by 4 11 1 as a v3 Parcelable blob copies into Room and decodes on read`() {
        val legacySong = Song().apply {
            code = "1"
            title = "Haleluya, Puji Tuhan"
            title_original = "Praise the Lord"
            authors_lyric = mutableListOf("Author L")
            authors_music = mutableListOf("Author M")
            tune = "TUNE-1"
            keySignature = "G"
            timeSignature = "4/4"
            scriptureReferences = "Psalm 150:1"
            lyrics = mutableListOf(
                Lyric().apply {
                    caption = "Bait 1"
                    verses = mutableListOf(
                        Verse().apply {
                            ordering = 1
                            kind = VerseKind.NORMAL
                            lines = mutableListOf("Haleluya puji Tuhan", "Kemuliaan bagi-Nya")
                        },
                        Verse().apply {
                            ordering = 2
                            kind = VerseKind.REFRAIN
                            lines = mutableListOf("Refrain line")
                        },
                    )
                },
            )
        }
        // Exactly the bytes 4.11.1 wrote: `Song.writeToParcelCompat` at
        // dataFormatVersion 3, in the pre-Android-13 Parcel layout.
        val legacyBlob = AospParcelWriter(ParcelLayout.LEGACY).write(legacySong, LEGACY_DATA_FORMAT_VERSION)

        seedSongDbAt4111 { db ->
            db.insert(Table.SongInfo.tableName(), null, ContentValues().apply {
                put(Table.SongInfo.bookName.name, "NKB")
                put(Table.SongInfo.code.name, "1")
                put(Table.SongInfo.title.name, "Haleluya, Puji Tuhan")
                put(Table.SongInfo.title_original.name, "Praise the Lord")
                put(Table.SongInfo.ordering.name, 1)
                put(Table.SongInfo.dataFormatVersion.name, LEGACY_DATA_FORMAT_VERSION)
                put(Table.SongInfo.data.name, legacyBlob)
                put(Table.SongInfo.updateTime.name, 1_600_000_000)
            })
            db.insert(Table.SongBookInfo.tableName(), null, ContentValues().apply {
                put(Table.SongBookInfo.name.name, "NKB")
                put(Table.SongBookInfo.title.name, "Nyanyikanlah Kidung Baru")
                put(Table.SongBookInfo.copyright.name, "(c) Yamuger")
            })
        }

        val roomDb = Room.inMemoryDatabaseBuilder(app, SongRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SongRoomDatabase.setForTesting(roomDb)

        withSongHelper { helper ->
            SongDbDataMigration.copyFromLegacyDbIfNeeded(roomDb, helper)
            val songDb = SongDb(helper)

            // The song book came across, so the songs screen still lists it.
            assertEquals(listOf("NKB"), songDb.listSongBookInfos().map { it.name })
            assertEquals("Nyanyikanlah Kidung Baru", songDb.getSongBookInfo("NKB")!!.title)
            assertEquals(listOf("1"), songDb.listSongInfosByBookName("NKB").map { it.code })

            // The v3 Parcelable payload decodes into a SongDocument with the
            // lyrics intact — not an empty shell and not an exception.
            val doc = songDb.getSong("NKB", "1")
            assertNotNull(doc)
            assertEquals("1", doc!!.code)
            assertEquals("Haleluya, Puji Tuhan", doc.meta.title)
            assertEquals("Praise the Lord", doc.meta.title_original)

            val lyricBlocks = doc.blocks.filterIsInstance<LyricBlock>()
            assertEquals(1, lyricBlocks.size)
            assertEquals("Bait 1", lyricBlocks[0].caption?.plainText())
            assertEquals(2, lyricBlocks[0].verses.size)
            assertEquals(
                listOf("Haleluya puji Tuhan", "Kemuliaan bagi-Nya"),
                lyricBlocks[0].verses[0].lines.map { it.plainText() },
            )
            assertEquals(listOf("Refrain line"), lyricBlocks[0].verses[1].lines.map { it.plainText() })

            // Read-time conversion is written back so the Parcelable decode
            // happens at most once per row.
            awaitDataFormatVersion("NKB", "1", SongDocumentJson.DATA_FORMAT_VERSION)

            // And the row still reads identically once it is JSON.
            assertEquals(doc, songDb.getSong("NKB", "1"))
        }
    }

    // endregion

    @Test
    fun `upgrade from 4 11 1 produces the same schema as a fresh install, for both databases`() {
        seedAlkitabDbAt4111 { }
        val upgradedAlkitab = withInternalHelper { helper ->
            helper.writableDatabase.let { schemaOf(it) to columnsOf(it) }
        }
        SQLiteDatabase.deleteDatabase(alkitabDbFile)
        val freshAlkitab = withInternalHelper { helper ->
            helper.writableDatabase.let { schemaOf(it) to columnsOf(it) }
        }
        assertEquals(freshAlkitab.first, upgradedAlkitab.first)
        assertEquals(freshAlkitab.second, upgradedAlkitab.second)

        // SongDb is still created, and still read by SongDbDataMigration, so
        // its schema has to stay in step too.
        seedSongDbAt4111 { }
        val upgradedSong = withSongHelper { helper ->
            helper.writableDatabase.let { schemaOf(it) to columnsOf(it) }
        }
        SQLiteDatabase.deleteDatabase(songDbFile)
        val freshSong = withSongHelper { helper ->
            helper.writableDatabase.let { schemaOf(it) to columnsOf(it) }
        }
        assertEquals(freshSong.first, upgradedSong.first)
        assertEquals(freshSong.second, upgradedSong.second)
    }

    private fun countOf(db: SQLiteDatabase, table: String, where: String? = null): Int {
        val sql = "select count(*) from $table" + if (where != null) " where $where" else ""
        db.rawQuery(sql, null).use { c ->
            c.moveToNext()
            return c.getInt(0)
        }
    }

    private fun awaitDataFormatVersion(bookName: String, code: String, expected: Int, timeoutMs: Long = 2000) {
        val roomDao = SongRoomDatabase.get(app).songRoomDao()
        fun current() = roomDao.findSongInfoByBookNameAndCode(bookName, code)?.dataFormatVersion
        val deadline = System.currentTimeMillis() + timeoutMs
        while (current() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertEquals(expected, current())
    }

    companion object {
        /** `versionCode` of 4.11.1, the last published non-beta release. */
        private const val VERSION_CODE_4_11_1 = 17000531

        /** The `dataFormatVersion` 4.11.1 requested from the server and stored. */
        private const val LEGACY_DATA_FORMAT_VERSION = 3

        /**
         * `AlkitabDb` schema as 4.11.1 left it, in the normalised form SQLite
         * stores in `sqlite_master.sql` (the `CREATE TABLE` keyword cased up,
         * `if not exists` dropped). Frozen on purpose: this is a fixture of a
         * *released* schema, so it must never be regenerated from the current
         * helper. When a release does add a table or column, add the
         * corresponding `onUpgrade` branch and leave this fixture alone.
         *
         * `android_metadata` is omitted — the platform creates it on open.
         */
        private val ALKITAB_DDL_4_11_1 = listOf(
            "CREATE TABLE Marker (_id integer primary key autoincrement, gid text,ari integer, kind integer, caption text, verseCount integer, createTime integer, modifyTime integer)",
            "CREATE INDEX index_Marker_01 on Marker (ari)",
            "CREATE INDEX index_Marker_02 on Marker (kind, ari)",
            "CREATE INDEX index_Marker_03 on Marker (kind, modifyTime)",
            "CREATE INDEX index_Marker_04 on Marker (kind, createTime)",
            "CREATE INDEX index_Marker_05 on Marker (kind, caption collate NOCASE)",
            "CREATE INDEX index_Marker_06 on Marker (gid)",
            "CREATE TABLE Devotion ( _id integer primary key ,name text,date text,body text,readyToUse integer,touchTime integer,dataFormatVersion integer)",
            "CREATE INDEX index_Devotion_01 on Devotion (name, date, dataFormatVersion)",
            "CREATE INDEX index_Devotion_02 on Devotion (touchTime)",
            "CREATE TABLE Label (_id integer primary key autoincrement, gid text,judul text, urutan integer, warnaLatar text)",
            "CREATE INDEX index_401 on Label (urutan)",
            "CREATE INDEX index_402 on Label (gid)",
            "CREATE TABLE Marker_Label (_id integer primary key autoincrement, gid text,marker_gid text, label_gid text)",
            "CREATE INDEX index_Marker_Label_01 on Marker_Label (marker_gid)",
            "CREATE INDEX index_Marker_Label_02 on Marker_Label (label_gid)",
            "CREATE UNIQUE INDEX index_Marker_Label_04 on Marker_Label (gid)",
            "CREATE TABLE ProgressMark (_id integer primary key autoincrement, preset_id integer, caption text, ari integer, modifyTime integer)",
            "CREATE INDEX index_601 on ProgressMark (preset_id)",
            "CREATE TABLE ProgressMarkHistory (_id integer primary key autoincrement, progress_mark_preset_id integer, progress_mark_caption integer, ari integer, createTime integer)",
            "CREATE INDEX index_701 on ProgressMarkHistory (progress_mark_preset_id, createTime)",
            "CREATE TABLE ReadingPlan (_id integer primary key autoincrement, version integer, name text, title text, description text, duration integer, startTime integer, data blob)",
            "CREATE TABLE ReadingPlanProgress (_id integer primary key autoincrement, reading_plan_progress_gid text, reading_code integer, checkTime integer)",
            "CREATE UNIQUE INDEX index_902 on ReadingPlanProgress (reading_plan_progress_gid, reading_code)",
            "CREATE TABLE Version (_id integer primary key autoincrement, locale text,shortName text,longName text,description text,filename text,preset_name text,modifyTime integer,active integer,ordering integer)",
            "CREATE INDEX index_Version_01 on Version (ordering)",
            "CREATE INDEX index_Version_02 on Version (active,longName)",
            "CREATE INDEX index_Version_03 on Version (preset_name)",
            "CREATE TABLE SyncShadow ( _id integer primary key ,syncSetName text,revno integer,data blob)",
            "CREATE INDEX index_SyncShadow_01 on SyncShadow (syncSetName)",
            "CREATE TABLE SyncLog ( _id integer primary key ,createTime integer,kind integer,syncSetName text,params text)",
            "CREATE INDEX index_SyncLog_01 on SyncLog (createTime)",
            "CREATE TABLE PerVersion ( _id integer primary key ,versionId text,settings text)",
            "CREATE UNIQUE INDEX index_PerVersion_01 on PerVersion (versionId)",
        )

        /** `SongDb` schema as 4.11.1 left it. Frozen for the same reason as [ALKITAB_DDL_4_11_1]. */
        private val SONG_DDL_4_11_1 = listOf(
            "CREATE TABLE SongInfo ( _id integer primary key ,bookName text,code text,title text collate nocase,title_original text collate nocase,ordering integer,dataFormatVersion integer,data blob,updateTime integer)",
            "CREATE INDEX SongInfo_001_index on SongInfo (bookName,code)",
            "CREATE INDEX SongInfo_002_index on SongInfo (bookName,ordering)",
            "CREATE TABLE SongBookInfo ( _id integer primary key ,name text,title text,copyright text)",
            "CREATE INDEX SongBookInfo_001_index on SongBookInfo (name)",
        )
    }
}
