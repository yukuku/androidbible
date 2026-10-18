package yuku.alkitab.base.appwidget

import android.app.Application
import android.content.Context
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.App
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.model.VersionImpl
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DailyVerseLoadingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val cache = DailyVerseData::class.java.getDeclaredField("dailyVerses").apply { isAccessible = true }

    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        cache.set(null, null)
    }

    @After
    fun tearDown() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        cache.set(null, null)
    }

    @Test
    fun `concurrent cold loads wait for the complete verse list`() {
        val bytes = yuku.afw.App.context.resources.openRawResource(R.raw.daily_verses_bt).use { it.readBytes() }
        val resources = mockk<Resources>()
        val context = mockk<Context>()
        every { context.resources } returns resources
        val isLoading = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { resources.openRawResource(R.raw.daily_verses_bt) } answers {
            isLoading.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            ByteArrayInputStream(bytes)
        }
        yuku.afw.App.context = context
        val version = mockk<Version>()
        every { version.loadVerseText(any()) } returns "Available"
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<IntArray?> {
                DailyVerseData.getAris(1, DailyVerseData.SavedState(), version, 1)
            }
            assertTrue(isLoading.await(5, TimeUnit.SECONDS))
            val secondStarted = CountDownLatch(1)
            val second = executor.submit<IntArray?> {
                secondStarted.countDown()
                DailyVerseData.getAris(2, DailyVerseData.SavedState(), version, 1)
            }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { second.get(200, TimeUnit.MILLISECONDS) }
            release.countDown()
            assertTrue(first.get(5, TimeUnit.SECONDS)!!.isNotEmpty())
            assertTrue(second.get(5, TimeUnit.SECONDS)!!.isNotEmpty())
        } finally {
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `a failed verse list read can be retried`() {
        val bytes = yuku.afw.App.context.resources.openRawResource(R.raw.daily_verses_bt).use { it.readBytes() }
        val resources = mockk<Resources>()
        val context = mockk<Context>()
        every { context.resources } returns resources
        every { resources.openRawResource(R.raw.daily_verses_bt) } returnsMany listOf(
            object : InputStream() {
                override fun read(): Int = throw IOException("Read failed")
            },
            ByteArrayInputStream(bytes),
        )
        yuku.afw.App.context = context
        val version = mockk<Version>()
        every { version.loadVerseText(any()) } returns "Available"
        assertThrows(RuntimeException::class.java) {
            DailyVerseData.getAris(1, DailyVerseData.SavedState(), version, 1)
        }
        assertTrue(DailyVerseData.getAris(1, DailyVerseData.SavedState(), version, 1)!!.isNotEmpty())
    }

    @Test
    fun `an existing corrupt Bible file falls back to the internal version`() {
        val file = File(tmp.root, "corrupt.yes").apply { writeText("invalid Bible file") }
        val selected = MVersionDb().apply { filename = file.absolutePath }
        val db = mockk<InternalDb>()
        every { db.listAllVersions() } returns listOf(selected)
        val storage = mockk<StorageProvider>()
        every { storage.db } returns db
        val services = App.services
        App.services = AppServices(storage, services.versions, services.uiDimensions)
        try {
            assertTrue(selected.hasDataFile())
            assertNull(selected.version)
            assertSame(VersionImpl.getInternalVersion(), DailyVerseData.getVersion(selected.versionId))
        } finally {
            App.services = services
        }
    }
}
