package yuku.alkitab.base.util

import android.app.Application
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.App

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class VersionDownloadWorkerTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `failed transfers delete partial files and successful transfers retain their bytes`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        App.context = context
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/partial") { exchange ->
            exchange.sendResponseHeaders(200, 100)
            try {
                exchange.responseBody.write("partial".toByteArray())
            } finally {
                exchange.close()
            }
        }
        server.createContext("/complete") { exchange ->
            val bytes = "complete version".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val destination = File(folder.root, "download.tmp")
            for (path in listOf("partial", "complete")) {
                val worker = TestListenableWorkerBuilder<VersionDownloadWorker>(context)
                    .setInputData(workDataOf(
                        VersionDownloadWorker.KEY_URL to "http://127.0.0.1:${server.address.port}/$path",
                        VersionDownloadWorker.KEY_DEST_PATH to destination.absolutePath,
                    ))
                    .build()
                val result = worker.doWork()
                if (path == "partial") {
                    assertEquals(ListenableWorker.Result.failure(workDataOf(
                        VersionDownloadWorker.KEY_ERROR_TYPE to VersionDownloadWorker.ERROR_CONNECTION,
                        VersionDownloadWorker.KEY_ERROR_MESSAGE to "unexpected end of stream",
                    )), result)
                    assertFalse(destination.exists())
                } else {
                    assertEquals(ListenableWorker.Result.success(workDataOf(
                        VersionDownloadWorker.KEY_DEST_PATH to destination.absolutePath,
                    )), result)
                    assertEquals("complete version", destination.readText())
                }
            }
        } finally {
            server.stop(0)
        }
    }
}
