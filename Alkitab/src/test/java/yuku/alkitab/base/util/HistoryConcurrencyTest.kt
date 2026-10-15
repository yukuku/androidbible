package yuku.alkitab.base.util

import android.app.Application
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class HistoryConcurrencyTest {
    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        Preferences.invalidate()
        History.replaceAllEntries(emptyList())
    }

    @Test
    fun `a snapshot cannot observe the empty intermediate state of a history replacement`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val entry = History.Entry("gid", 123, 456L, "device")
        val replacement = object : AbstractList<History.Entry>() {
            override val size = 1
            override fun get(index: Int): History.Entry {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                return entry
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writer = executor.submit { History.replaceAllEntries(replacement) }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val readerStarted = CountDownLatch(1)
            val reader = executor.submit<List<History.Entry>> {
                readerStarted.countDown()
                History.listAllEntries()
            }
            assertTrue(readerStarted.await(10, TimeUnit.SECONDS))
            try { reader.get(200, TimeUnit.MILLISECONDS) } catch (_: TimeoutException) { }
            release.countDown()
            writer.get(10, TimeUnit.SECONDS)
            val snapshot = reader.get(10, TimeUnit.SECONDS)
            assertEquals(listOf(entry), snapshot)
            History.replaceAllEntries(emptyList())
            assertEquals(listOf(entry), snapshot)
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
