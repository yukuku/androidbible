package yuku.alkitab.base.storage

import android.app.Application
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.model.PerVersionSettings
import yuku.alkitab.base.storage.room.AppDatabase

/**
 * Robolectric tests for [PerVersionDao]. Originally exercised the
 * SQLite-backed implementation against [InternalDbHelper]; after the
 * PerVersion → Room migration the facade routes through Room, so this test
 * now installs an in-memory [AppDatabase] in [setUp].
 *
 * The behavioural contract is unchanged — these tests are the parity gate
 * for the Room migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PerVersionDaoTest {
    private lateinit var helper: InternalDbHelper
    private lateinit var roomDb: AppDatabase
    private lateinit var dao: PerVersionDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        // InternalDbHelper is still needed by the legacy PerVersionDao
        // constructor signature, but the facade ignores it now.
        helper = InternalDbHelper(app)
        roomDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setForTesting(roomDb)
        dao = PerVersionDao(helper)
    }

    @After
    fun tearDown() {
        AppDatabase.setForTesting(null)
        helper.close()
    }

    @Test
    fun `getSettings returns default settings when no row exists`() {
        val loaded = dao.getSettings("preset/kjv")
        assertEquals(1.0f, loaded.fontSizeMultiplier, 0.0f)
    }

    @Test
    fun `storeSettings inserts then getSettings round-trips the value`() {
        val settings = PerVersionSettings().apply { fontSizeMultiplier = 1.5f }
        dao.storeSettings("preset/kjv", settings)

        val loaded = dao.getSettings("preset/kjv")
        assertEquals(1.5f, loaded.fontSizeMultiplier, 0.0f)
    }

    @Test
    fun `storeSettings upserts in place when versionId already has a row`() {
        dao.storeSettings("preset/kjv", PerVersionSettings().apply { fontSizeMultiplier = 1.2f })
        dao.storeSettings("preset/kjv", PerVersionSettings().apply { fontSizeMultiplier = 2.0f })

        assertEquals(2.0f, dao.getSettings("preset/kjv").fontSizeMultiplier, 0.0f)
    }

    @Test
    fun `getSettings for a different versionId is isolated from other stored rows`() {
        dao.storeSettings("preset/a", PerVersionSettings().apply { fontSizeMultiplier = 1.2f })
        dao.storeSettings("preset/b", PerVersionSettings().apply { fontSizeMultiplier = 0.8f })

        assertEquals(1.2f, dao.getSettings("preset/a").fontSizeMultiplier, 0.0f)
        assertEquals(0.8f, dao.getSettings("preset/b").fontSizeMultiplier, 0.0f)
    }
}
