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
 * contract that prevents stale temp-file resume across separate downloads of
 * the same Bible version (e.g. user downloading an *update* to a preset they
 * already have).
 *
 * Background: [VersionDownloadWorker] writes to a temp file whose name is
 * derived deterministically from the `downloadKey`. The worker supports
 * Range-based resume, so if the temp file already exists when a fresh
 * download starts it will send `Range: bytes=N-`. That resume mechanism is
 * only valid between retries of the *same* download — for two unrelated
 * downloads of the same preset (e.g. initial download → server update later)
 * it produces a 416 from the server (visible as a "Cannot connect to server"
 * generic error) or a corrupted file (if the new file is larger than the
 * stale temp).
 *
 * [DownloadMapper.consumeAndRemove] is the receiver-side hook that deletes
 * the temp file once the download has been finalized, severing that loop.
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
            "consumeAndRemove must delete the temp file so the next download with the same key doesn't resume from stale bytes",
            tempFile.exists(),
        )
        assertNull("row must be removed from the in-memory map after consumption", mapper.getDownloadedFilePath(id))
    }

    @Test
    fun `remove preserves the temp file so failed downloads can be resumed`() {
        val mapper = DownloadMapper.instance
        val key = "version:preset_name:test-remove-${System.nanoTime()}"

        val id = mapper.seedRowForTest(key, tempFile.absolutePath)
        mapper.remove(id)

        // `remove` is the cancel/failure path: keeping the partial temp file
        // alive is intentional — the next attempt will resume via Range.
        assertTrue(
            "remove must NOT delete the temp file (preserves Range-resume on retry of a failed download)",
            tempFile.exists(),
        )
        assertNull("row must be removed from the in-memory map", mapper.getDownloadedFilePath(id))
    }
}
