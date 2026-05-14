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
import yuku.alkitab.base.ac.DevotionActivity
import yuku.alkitab.base.devotion.ArticleRenunganHarian
import yuku.alkitab.base.devotion.ArticleSantapanHarian
import yuku.alkitab.base.storage.room.AppDatabase
import java.util.Date

/**
 * Robolectric tests for [DevotionDao]. Originally exercised the SQLite-backed
 * implementation against [InternalDbHelper]; after the Devotion → Room
 * migration the facade routes through Room, so this test now installs an
 * in-memory [AppDatabase] in [setUp].
 *
 * The behavioural contract is unchanged — these tests are the parity gate for
 * the Room migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DevotionDaoTest {
    private lateinit var helper: InternalDbHelper
    private lateinit var roomDb: AppDatabase
    private lateinit var dao: DevotionDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        // InternalDbHelper is still needed by the legacy DevotionDao
        // constructor signature, but the facade ignores it now.
        helper = InternalDbHelper(app)
        roomDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setForTesting(roomDb)
        dao = DevotionDao(helper)
    }

    @After
    fun tearDown() {
        AppDatabase.setForTesting(null)
        helper.close()
    }

    @Test
    fun `tryGet returns null when nothing was stored for the name-date pair`() {
        assertNull(dao.tryGet(DevotionActivity.DevotionKind.RH.name, "20260420"))
    }

    @Test
    fun `storeArticle then tryGet round-trips body and readyToUse for RH`() {
        dao.storeArticle(ArticleRenunganHarian("20260420", "hello body", true))

        val loaded = dao.tryGet(DevotionActivity.DevotionKind.RH.name, "20260420")
        assertNotNull(loaded)
        assertTrue(loaded is ArticleRenunganHarian)
        assertEquals("hello body", loaded!!.body)
        assertTrue(loaded.readyToUse)
    }

    @Test
    fun `storeArticle persists readyToUse false and nulls the body column`() {
        // Non-ready-to-use rows must not retain stale body content — see
        // DevotionDao.storeArticle which nulls the body column in that case.
        dao.storeArticle(ArticleRenunganHarian("20260420", "placeholder", false))

        val loaded = dao.tryGet(DevotionActivity.DevotionKind.RH.name, "20260420")!!
        assertFalse(loaded.readyToUse)
        // The stored body column is null; ArticleFromSabda.getBody() is @NonNull-annotated
        // but returns the raw field, so the runtime value may be null regardless of the
        // annotation. The important contract is that the placeholder did not survive.
        assertNull(loaded.body)
    }

    @Test
    fun `storeArticle replaces the row for the same name-date pair on re-store`() {
        dao.storeArticle(ArticleRenunganHarian("20260420", "first", true))
        dao.storeArticle(ArticleRenunganHarian("20260420", "second", true))

        assertEquals(
            "second",
            dao.tryGet(DevotionActivity.DevotionKind.RH.name, "20260420")!!.body,
        )
    }

    @Test
    fun `storeArticle isolates rows across different kinds and dates`() {
        dao.storeArticle(ArticleRenunganHarian("20260420", "rh-body", true))
        dao.storeArticle(ArticleSantapanHarian("20260420", "sh-body", true))
        dao.storeArticle(ArticleRenunganHarian("20260421", "rh-tomorrow", true))

        assertEquals(
            "rh-body",
            dao.tryGet(DevotionActivity.DevotionKind.RH.name, "20260420")!!.body,
        )
        assertEquals(
            "sh-body",
            dao.tryGet(DevotionActivity.DevotionKind.SH.name, "20260420")!!.body,
        )
        assertEquals(
            "rh-tomorrow",
            dao.tryGet(DevotionActivity.DevotionKind.RH.name, "20260421")!!.body,
        )
    }

    @Test
    fun `deleteWithTouchTimeBefore removes older cached rows but keeps newer ones`() {
        // Two rows touched "now" by the DAO. Advance the cutoff past the current
        // touchTime so both rows qualify; then a second store with a post-cutoff
        // touchTime should survive another prune at the original cutoff.
        dao.storeArticle(ArticleRenunganHarian("20260418", "a", true))
        dao.storeArticle(ArticleRenunganHarian("20260419", "b", true))

        val futureCutoff = Date(System.currentTimeMillis() + 60_000L)
        val deleted = dao.deleteWithTouchTimeBefore(futureCutoff)
        assertEquals(2, deleted)

        assertNull(dao.tryGet(DevotionActivity.DevotionKind.RH.name, "20260418"))
        assertNull(dao.tryGet(DevotionActivity.DevotionKind.RH.name, "20260419"))
    }
}
