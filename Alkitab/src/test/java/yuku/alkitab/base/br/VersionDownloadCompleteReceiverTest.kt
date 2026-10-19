package yuku.alkitab.base.br

import android.app.Application
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class VersionDownloadCompleteReceiverTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `corrupt gzip rolls back the destination and removes the pending write`() {
        val source = folder.newFile("source")
        val destination = folder.newFile("destination").apply { writeText("existing version") }
        val bytes = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write("new version".repeat(1000).toByteArray()) }
        }.toByteArray()
        bytes[bytes.size - 8] = (bytes[bytes.size - 8].toInt() xor 1).toByte()
        source.writeBytes(bytes)
        repeat(3) {
            assertThrows(IOException::class.java) {
                VersionDownloadCompleteReceiver.finalizeDownload(source, destination)
            }
            assertEquals("existing version", destination.readText())
            assertFalse(File(destination.path + ".new").exists())
        }
    }

    @Test
    fun `plain and gzip downloads replace the destination`() {
        val source = folder.newFile("source")
        val destination = folder.newFile("destination")
        source.writeText("plain version")
        VersionDownloadCompleteReceiver.finalizeDownload(source, destination)
        assertEquals("plain version", destination.readText())
        source.outputStream().use { output ->
            GZIPOutputStream(output).use { it.write("compressed version".toByteArray()) }
        }
        VersionDownloadCompleteReceiver.finalizeDownload(source, destination)
        assertEquals("compressed version", destination.readText())
    }
}
