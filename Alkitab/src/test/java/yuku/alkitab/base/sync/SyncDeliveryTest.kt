package yuku.alkitab.base.sync

import android.app.Application
import android.content.Context
import android.content.SyncStats
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.model.SyncShadow
import yuku.alkitab.base.storage.Prefkey

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SyncDeliveryTest {
    private val executor = Executors.newFixedThreadPool(2)
    private val started = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val delivered = CopyOnWriteArrayList<String>()
    private var isFirstAttemptFailed = false
    private var isFirstAttemptThrowing = false
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = context
        Preferences.invalidate()
        Preferences.setString(Prefkey.sync_simpleToken, "test-token")
        mockkStatic(SyncRecorder::class)
        every { SyncRecorder.log(any(), any(), *anyVararg()) } answers { }
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder()
                .setExecutor(executor)
                .setTaskExecutor(SynchronousExecutor())
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker {
                        return object : SyncAdapter(appContext, workerParameters) {
                            override fun syncMabel(stats: SyncStats) {
                                delivered.add(SyncShadow.SYNC_SET_MABEL)
                                started.countDown()
                                check(release.await(10, TimeUnit.SECONDS))
                                if (isFirstAttemptThrowing) throw IllegalStateException("test failure")
                                if (isFirstAttemptFailed) stats.numIoExceptions++
                            }
                            override fun syncPins(stats: SyncStats) { delivered.add(SyncShadow.SYNC_SET_PINS) }
                            override fun syncRp(stats: SyncStats) { delivered.add(SyncShadow.SYNC_SET_RP) }
                        }
                    }
                }).build())
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        release.countDown()
        workManager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        WorkManagerTestInitHelper.closeWorkDatabase()
        unmockkAll()
    }

    private fun workInfos() = workManager.getWorkInfosForUniqueWork("sync").get(10, TimeUnit.SECONDS)

    private fun awaitFinished() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (workInfos().isNotEmpty() && workInfos().all { it.state.isFinished }) return
            Thread.sleep(10)
        }
        throw AssertionError("Sync work did not finish: ${workInfos()}")
    }

    @Test
    fun `a request during running sync is delivered after that attempt finishes`() {
        SyncKotlin.syncNow(true, arrayOf(SyncShadow.SYNC_SET_MABEL))
        assertTrue(started.await(10, TimeUnit.SECONDS))
        SyncKotlin.syncNow(true, arrayOf(SyncShadow.SYNC_SET_PINS))
        release.countDown()
        awaitFinished()
        assertEquals(listOf(SyncShadow.SYNC_SET_MABEL, SyncShadow.SYNC_SET_PINS), delivered)
        assertFalse(workInfos().any { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `a failed sync attempt does not discard a request already queued behind it`() {
        isFirstAttemptFailed = true
        SyncKotlin.syncNow(true, arrayOf(SyncShadow.SYNC_SET_MABEL))
        assertTrue(started.await(10, TimeUnit.SECONDS))
        SyncKotlin.syncNow(true, arrayOf(SyncShadow.SYNC_SET_RP))
        release.countDown()
        awaitFinished()
        assertEquals(listOf(SyncShadow.SYNC_SET_MABEL, SyncShadow.SYNC_SET_RP), delivered)
        assertEquals(1, workInfos().count { !it.outputData.getBoolean(SyncAdapter.EXTRA_IS_SYNC_SUCCESSFUL, true) })
    }

    @Test
    fun `requests queued before a worker starts retain both sets`() {
        SyncKotlin.syncNow(false, arrayOf(SyncShadow.SYNC_SET_PINS))
        SyncKotlin.syncNow(false, arrayOf(SyncShadow.SYNC_SET_RP))
        val infos = workInfos()
        assertEquals(2, infos.size)
        val driver = WorkManagerTestInitHelper.getTestDriver(RuntimeEnvironment.getApplication())!!
        infos.forEach { driver.setAllConstraintsMet(it.id) }
        awaitFinished()
        assertEquals(listOf(SyncShadow.SYNC_SET_PINS, SyncShadow.SYNC_SET_RP), delivered)
        assertFalse(workInfos().any { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `a second request for the running set causes another attempt`() {
        SyncKotlin.syncNow(true, arrayOf(SyncShadow.SYNC_SET_MABEL))
        assertTrue(started.await(10, TimeUnit.SECONDS))
        SyncKotlin.syncNow(true, arrayOf(SyncShadow.SYNC_SET_MABEL))
        release.countDown()
        awaitFinished()
        assertEquals(listOf(SyncShadow.SYNC_SET_MABEL, SyncShadow.SYNC_SET_MABEL), delivered)
    }

    @Test
    fun `an unexpected set failure allows other sets and queued requests to run`() {
        isFirstAttemptThrowing = true
        SyncKotlin.syncNow(true, arrayOf(SyncShadow.SYNC_SET_MABEL, SyncShadow.SYNC_SET_PINS))
        assertTrue(started.await(10, TimeUnit.SECONDS))
        SyncKotlin.syncNow(true, arrayOf(SyncShadow.SYNC_SET_RP))
        release.countDown()
        awaitFinished()
        assertEquals(listOf(SyncShadow.SYNC_SET_MABEL, SyncShadow.SYNC_SET_PINS, SyncShadow.SYNC_SET_RP), delivered)
        assertTrue(SyncAdapter.getRunningSyncs().isEmpty())
    }

    @Test
    fun `empty requests do not create work`() {
        SyncKotlin.syncNow(false, emptyArray())
        assertTrue(workInfos().isEmpty())
    }

    @Test
    fun `a suppressed first set does not discard the remaining set names`() {
        Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_MABEL, true)
        try {
            Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL, SyncShadow.SYNC_SET_PINS)
        } finally {
            Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_MABEL, false)
        }
        val infos = workInfos()
        assertEquals(1, infos.size)
        WorkManagerTestInitHelper.getTestDriver(RuntimeEnvironment.getApplication())!!
            .setAllConstraintsMet(infos.single().id)
        awaitFinished()
        assertEquals(listOf(SyncShadow.SYNC_SET_PINS), delivered)
    }
}
