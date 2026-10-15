package yuku.alkitab.base.sync

import android.app.Application
import android.database.sqlite.SQLiteException
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class SyncRecordingTest {
    private lateinit var helper: InternalDbHelper
    private lateinit var db: InternalDb
    private lateinit var original: AppServices

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        Preferences.invalidate()
        helper = InternalDbHelper(app)
        db = InternalDb(helper)
        original = App.services
        val storage = mockk<StorageProvider>()
        every { storage.db } returns db
        App.services = AppServices(storage, mockk(), mockk())
        Preferences.remove(Prefkey.sync_simpleToken)
        Preferences.setBoolean(Sync.prefkeyForSyncSetEnabled("mabel"), true)
    }

    @After
    fun tearDown() {
        App.services = original
        helper.close()
    }

    private fun worker(vararg sets: String): SyncAdapter {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        every { params.inputData } returns Data.Builder().putStringArray(SyncAdapter.EXTRA_SYNC_SET_NAMES, arrayOf(*sets)).build()
        return SyncAdapter(RuntimeEnvironment.getApplication(), params)
    }

    private fun delivered(isSuccessful: Boolean) = ListenableWorker.Result.success(
        Data.Builder().putBoolean(SyncAdapter.EXTRA_IS_SYNC_SUCCESSFUL, isSuccessful).build())
    private fun rows() = db.listLatestSyncLog(500)
    private fun recorded(kind: SyncRecorder.EventKind) = rows().any { it.kind_code == kind.code }

    @Test
    fun `missing credentials and empty input explain no work while retaining delivery success`() {
        Sync.notifySyncNeeded("mabel")
        assertEquals("not_logged_in", rows().first().params["reason"])
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertTrue(recorded(SyncRecorder.EventKind.worker_result_succeeded_no_work_needed))
        assertEquals(delivered(false), worker("mabel").doWork())
        assertTrue(recorded(SyncRecorder.EventKind.error_no_simple_token))
        assertTrue(recorded(SyncRecorder.EventKind.worker_result_failed))
        assertEquals(false, rows().first().params["is_sync_successful"])
    }

    @Test
    fun `disabled and unknown sets are observable without changing worker results`() {
        Preferences.setBoolean(Sync.prefkeyForSyncSetEnabled("mabel"), false)
        assertEquals(delivered(true), worker("mabel").doWork())
        assertTrue(recorded(SyncRecorder.EventKind.sync_adapter_set_not_enabled))
        assertEquals(delivered(true), worker("private note").doWork())
        assertTrue(rows().any { it.params["reason"] == "unknown_set" })
        assertFalse(rows().toString().contains("private note"))
    }

    @Test
    fun `exceptions retain category and types while dropping all sensitive text`() {
        SyncRecorder.error("apply", "mabel", SQLiteException("person@example.org private note password"))
        val row = rows().first()
        assertEquals("DATABASE", row.params["category"])
        assertEquals("apply", row.params["stage"])
        assertEquals("android.database.sqlite.SQLiteException", row.params["causes"])
        SyncRecorder.log(SyncRecorder.EventKind.login_failed, null, "email", "person@example.org", "message", "private note", "duration_ms", 12)
        assertEquals(mapOf("duration_ms" to 12L), rows().first().params)
    }

    @Test
    fun `retention keeps the latest 500 events including equal timestamps`() {
        repeat(510) { SyncRecorder.log(SyncRecorder.EventKind.diagnostic_stage, null, "attempt", it) }
        val rows = rows()
        assertEquals(500, rows.size)
        assertEquals(509L, rows.first().params["attempt"])
        assertEquals(10L, rows.last().params["attempt"])
    }

    @Test
    fun `legacy sensitive and malformed parameters cannot escape through log reads`() {
        db.insertSyncLog(1, SyncRecorder.EventKind.login_failed, "person@example.org", """{"email":"person@example.org","message":"private note"}""")
        assertTrue(rows().first().params.isEmpty())
        assertEquals("unknown", rows().first().syncSetName)
        db.insertSyncLog(2, SyncRecorder.EventKind.login_failed, null, "invalid JSON")
        assertTrue(rows().first().params.isEmpty())
    }

    @Test
    fun `stopping a worker is recorded without changing cancellation behavior`() {
        worker("mabel").onStopped()
        assertTrue(recorded(SyncRecorder.EventKind.worker_stopped))
    }

    @Test
    fun `worker runtime failures are recorded while preserving per-set isolation`() {
        val worker = spyk(worker("mabel"))
        every { worker.syncMabel(any()) } throws SQLiteException("private note")
        assertEquals(delivered(false), worker.doWork())
        assertTrue(recorded(SyncRecorder.EventKind.worker_result_failed))
        assertTrue(recorded(SyncRecorder.EventKind.sync_adapter_error_unexpected))
        assertTrue(rows().any { it.params["category"] == "DATABASE" })
        assertTrue(SyncAdapter.getRunningSyncs().isEmpty())
    }

    @Test
    fun `HTTP authentication status is recorded without headers or response content`() {
        val call = mockk<okhttp3.Call>()
        every { call.execute() } returns okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.org/?token=secret").build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(401).message("private message")
            .header("Authorization", "secret").body("private note".toResponseBody()).build()
        assertEquals("private note", worker().execute(call, "mabel"))
        val row = rows().first()
        assertEquals(401L, row.params["http_status"])
        assertEquals("AUTH", row.params["category"])
        assertEquals(2, row.params.size)
    }

    @Test
    fun `scheduler initialization failure is visible instead of escaping the click handler`() {
        SyncKotlin.syncNow(true)
        assertTrue(recorded(SyncRecorder.EventKind.schedule_requested))
        assertTrue(rows().any { it.kind_code == SyncRecorder.EventKind.diagnostic_error.code && it.params["stage"] == "schedule" })
    }

    @Test
    fun `database logging failures use a bounded memory fallback without throwing`() {
        val storage = mockk<StorageProvider>()
        every { storage.db } throws SQLiteException("secret database path")
        App.services = AppServices(storage, mockk(), mockk())
        repeat(60) { SyncRecorder.error("apply", "mabel", SQLiteException("private note")) }
        val rows = SyncRecorder.fallbackLogs()
        assertEquals(50, rows.size)
        assertTrue(rows.all { it.params["category"] == "DATABASE" })
        assertFalse(rows.map { it.params }.toString().contains("private note"))
    }

    @Test
    fun `oversized legacy log blobs are pruned without touching sync data`() {
        db.insertSyncLog(1, SyncRecorder.EventKind.login_failed, "x".repeat(5000), "x".repeat(5000))
        helper.readableDatabase.rawQuery("SELECT params, syncSetName FROM SyncLog", null).use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
            assertEquals("unknown", it.getString(1))
        }
    }
    @Test
    fun `empty scheduling requests are observable without initializing WorkManager`() {
        SyncKotlin.syncNow(false, emptyArray())
        assertEquals("empty_request", rows().first().params["reason"])
        assertTrue(recorded(SyncRecorder.EventKind.schedule_requested))
        assertFalse(recorded(SyncRecorder.EventKind.diagnostic_error))
    }

}
