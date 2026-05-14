package yuku.alkitab.base.util

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.App

/**
 * Lifecycle tests for [DownloadMapper], focused on the post-download cleanup
 * contract.
 *
 * Background: [VersionDownloadWorker] writes to a temp file whose name is
 * derived deterministically from the `downloadKey`. The worker always
 * starts from byte 0 (no Range-based resume), so leftover bytes in cache
 * are never trusted — but cleaning them up after the receiver consumes
 * them keeps the cache from accumulating dead files.
 *
 * [DownloadMapper.consumeAndRemove] is the receiver-side hook that does
 * that cleanup once the download has been finalized.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DownloadMapperTest {
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        App.context = app

        // `DownloadMapper.remove()` calls into WorkManager. The production app
        // initialises WorkManager via its `Configuration.Provider`; in tests we
        // bring up a minimal instance so the cancel call doesn't blow up.
        if (!WorkManager.isInitialized()) {
            val config = Configuration.Builder()
                .setExecutor(Executors.newSingleThreadExecutor())
                .setTaskExecutor(Executors.newSingleThreadExecutor())
                .build()
            WorkManager.initialize(app, config)
        }

        val dir = File(app.cacheDir, "DownloadMapper-tmp").apply { mkdirs() }
        tempFile = File(dir, "DownloadMapper-test-${System.nanoTime()}.tmp")
        tempFile.writeBytes(ByteArray(1024) { it.toByte() })
        assertTrue("precondition: temp file should exist", tempFile.exists())
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun `consumeAndRemove deletes the temp file and drops the row`() {
        val mapper = DownloadMapper.instance
        val key = "version:preset_name:test-consume-${System.nanoTime()}"

        val id = mapper.seedRowForTest(key, tempFile.absolutePath)

        // Sanity: the mapper sees the row before consumption.
        assertTrue("downloaded file path should resolve before consumption", mapper.getDownloadedFilePath(id) == tempFile.absolutePath)

        mapper.consumeAndRemove(id)

        assertFalse(
            "consumeAndRemove must delete the temp file so the cache doesn't accumulate stale download leftovers",
            tempFile.exists(),
        )
        assertNull("row must be removed from the in-memory map after consumption", mapper.getDownloadedFilePath(id))
    }

    @Test
    fun `remove drops the row without touching the temp file`() {
        val mapper = DownloadMapper.instance
        val key = "version:preset_name:test-remove-${System.nanoTime()}"

        val id = mapper.seedRowForTest(key, tempFile.absolutePath)
        mapper.remove(id)

        // `remove` is the cancel/failure path. It doesn't delete the temp
        // file: cleanup is the next worker's job (the worker always starts
        // from byte 0 and discards any pre-existing destFile).
        assertTrue(
            "remove must not touch the temp file",
            tempFile.exists(),
        )
        assertNull("row must be removed from the in-memory map", mapper.getDownloadedFilePath(id))
    }
}
