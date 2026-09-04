package yuku.alkitab.base.audio.download

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.audio.builtin.BuiltInAudioCatalog

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AudioChapterDownloadWorkerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var store: AudioChapterDownloadStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = AudioChapterDownloadStore(temporaryFolder.root)
        AudioChapterDownloadWorker.storeProvider = { store }
    }

    @After
    fun tearDown() {
        AudioChapterDownloadWorker.resetForTest()
    }

    @Test
    fun `successful MPEG response is published atomically`() = runBlocking {
        AudioChapterDownloadWorker.httpClient = fakeClient(
            finalUrl = VALID_URL,
            contentType = "audio/mpeg",
            bytes = "ID3audio".toByteArray(),
        )

        assertEquals(ListenableWorker.Result.success(), runWorker(VALID_URL))
        assertTrue(store.isDownloaded(BuiltInAudioCatalog.AUDIO_ID, 0, 1))
        assertFalse(store.tempFile(BuiltInAudioCatalog.AUDIO_ID, 0, 1).exists())
    }

    @Test
    fun `HTML response fails and removes the partial file`() = runBlocking {
        AudioChapterDownloadWorker.httpClient = fakeClient(
            finalUrl = VALID_URL,
            contentType = "text/html",
            bytes = "not audio".toByteArray(),
        )

        assertEquals(ListenableWorker.Result.failure(), runWorker(VALID_URL))
        assertFalse(store.isDownloaded(BuiltInAudioCatalog.AUDIO_ID, 0, 1))
        assertFalse(store.tempFile(BuiltInAudioCatalog.AUDIO_ID, 0, 1).exists())
    }

    @Test
    fun `redirect away from the licensed HTTPS host is rejected`() = runBlocking {
        AudioChapterDownloadWorker.httpClient = fakeClient(
            finalUrl = "https://evil.example/chapter.mp3",
            contentType = "audio/mpeg",
            bytes = "ID3audio".toByteArray(),
        )

        assertEquals(ListenableWorker.Result.failure(), runWorker(VALID_URL))
        assertFalse(store.isDownloaded(BuiltInAudioCatalog.AUDIO_ID, 0, 1))
    }

    @Test
    fun `cleartext source URL is rejected before a request`() = runBlocking {
        var calls = 0
        AudioChapterDownloadWorker.httpClient = AudioChapterHttpClient {
            calls++
            response(VALID_URL, "audio/mpeg", "ID3audio".toByteArray())
        }

        assertEquals(ListenableWorker.Result.failure(), runWorker("http://audiotreasure.com/chapter.mp3"))
        assertEquals(0, calls)
    }

    @Test
    fun `invalid coordinates fail without leaving a partial file`() = runBlocking {
        AudioChapterDownloadWorker.httpClient = fakeClient(VALID_URL, "audio/mpeg", "ID3audio".toByteArray())

        assertEquals(ListenableWorker.Result.failure(), runWorker(VALID_URL, bookId = 66))
    }

    private suspend fun runWorker(url: String, bookId: Int = 0): ListenableWorker.Result {
        val input = workDataOf(
            AudioChapterDownloadWorker.KEY_AUDIO_ID to BuiltInAudioCatalog.AUDIO_ID,
            AudioChapterDownloadWorker.KEY_BOOK_ID to bookId,
            AudioChapterDownloadWorker.KEY_CHAPTER_1 to 1,
            AudioChapterDownloadWorker.KEY_URL to url,
        )
        val worker = TestListenableWorkerBuilder<AudioChapterDownloadWorker>(context)
            .setInputData(input)
            .build()
        return worker.doWork()
    }

    private fun fakeClient(finalUrl: String, contentType: String, bytes: ByteArray) =
        AudioChapterHttpClient { response(finalUrl, contentType, bytes) }

    private fun response(finalUrl: String, contentType: String, bytes: ByteArray) =
        AudioChapterHttpResponse(
            isSuccessful = true,
            finalUrl = finalUrl,
            contentType = contentType,
            contentLength = bytes.size.toLong(),
            body = ByteArrayInputStream(bytes),
            closeAction = {},
        )

    private companion object {
        const val VALID_URL = "https://audiotreasure.com/content/WEBD_AT/01_Genesis_01.mp3"
    }
}
