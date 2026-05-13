package yuku.alkitab.base.storage.room

import android.app.Application
import android.content.ContentValues
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.InternalDbHelper

/**
 * Verifies the one-time copy from the legacy `Version` table into Room's
 * `version` table. Robolectric is required because [InternalDbHelper] extends
 * [android.database.sqlite.SQLiteOpenHelper].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class VersionDataMigrationTest {
    private lateinit var legacy: InternalDbHelper
    private lateinit var room: AppDatabase

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        legacy = InternalDbHelper(app)
        room = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        room.close()
        legacy.close()
    }

    private fun insertLegacyRow(
        filename: String,
        ordering: Int,
        presetName: String? = null,
        active: Int = 1,
        longName: String = "Long",
        modifyTime: Int = 1_700_000_000,
    ) {
        val cv = ContentValues().apply {
            put(Db.Version.filename, filename)
            put(Db.Version.preset_name, presetName)
            put(Db.Version.ordering, ordering)
            put(Db.Version.active, active)
            put(Db.Version.locale, "en")
            put(Db.Version.shortName, "SN")
            put(Db.Version.longName, longName)
            put(Db.Version.description, "desc")
            put(Db.Version.modifyTime, modifyTime)
        }
        legacy.writableDatabase.insert(Db.TABLE_Version, null, cv)
    }

    @Test
    fun `copy is a no-op when both databases are empty`() {
        VersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(room.versionDao().listAll().isEmpty())
    }

    @Test
    fun `copy moves every legacy row into Room ordered by ordering ascending`() {
        insertLegacyRow("/b.yes", 102, longName = "B")
        insertLegacyRow("/a.yes", 101, longName = "A")
        insertLegacyRow("/c.yes", 103, longName = "C")

        VersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.versionDao().listAll()
        assertEquals(listOf("/a.yes", "/b.yes", "/c.yes"), rows.map { it.filename })
        assertEquals(listOf(101, 102, 103), rows.map { it.ordering })
        assertEquals(listOf("A", "B", "C"), rows.map { it.longName })
    }

    @Test
    fun `copy round-trips every persisted column including preset_name and active`() {
        insertLegacyRow(
            filename = "/a.yes",
            ordering = 101,
            presetName = "kjv",
            active = 1,
            longName = "King James",
            modifyTime = 1_700_000_111,
        )

        VersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val loaded = room.versionDao().findByFilename("/a.yes")
        assertNotNull(loaded)
        with(loaded!!) {
            assertEquals("/a.yes", filename)
            assertEquals("kjv", preset_name)
            assertEquals(101, ordering)
            assertEquals(1, active)
            assertEquals("King James", longName)
            assertEquals(1_700_000_111, modifyTime)
            assertEquals("en", locale)
            assertEquals("SN", shortName)
            assertEquals("desc", description)
        }
    }

    @Test
    fun `copy is idempotent — running twice does not duplicate rows`() {
        insertLegacyRow("/a.yes", 101)

        VersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        VersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(1, room.versionDao().listAll().size)
    }

    @Test
    fun `copy skips when Room already has rows (regardless of legacy content)`() {
        // Simulate a previous-run state: Room has rows, legacy table happens
        // to have a different row that hasn't been copied. The migration must
        // not stomp on the already-populated Room table.
        room.versionDao().insert(
            VersionEntity(
                _id = 0L,
                locale = "en",
                shortName = "SN",
                longName = "Existing",
                description = "desc",
                filename = "/existing.yes",
                preset_name = null,
                modifyTime = 0,
                active = 1,
                ordering = 500,
            ),
        )
        insertLegacyRow("/a.yes", 101)

        VersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.versionDao().listAll()
        assertEquals(1, rows.size)
        assertEquals("/existing.yes", rows.single().filename)
    }
}
