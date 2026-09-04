package yuku.alkitab.base.audio.download

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.audio.builtin.BuiltInAudioCatalog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioChapterDownloadStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val store by lazy { AudioChapterDownloadStore(temporaryFolder.root) }

    @Test
    fun `publish atomically replaces the exact temp file with the final chapter`() {
        val temp = store.tempFile(BuiltInAudioCatalog.AUDIO_ID, 0, 1)
        temp.writeBytes("ID3audio".toByteArray())

        store.publishTemp(BuiltInAudioCatalog.AUDIO_ID, 0, 1, temp)

        assertFalse(temp.exists())
        assertEquals("ID3audio", store.localFile(BuiltInAudioCatalog.AUDIO_ID, 0, 1).readText())
        assertTrue(store.isDownloaded(BuiltInAudioCatalog.AUDIO_ID, 0, 1))
        assertEquals(
            store.localFile(BuiltInAudioCatalog.AUDIO_ID, 0, 1).absolutePath,
            store.localUri(BuiltInAudioCatalog.AUDIO_ID, 0, 1)?.path,
        )
    }

    @Test
    fun `an unfinished temp file is never reported as downloaded`() {
        store.tempFile(BuiltInAudioCatalog.AUDIO_ID, 39, 1).writeBytes("ID3partial".toByteArray())

        assertFalse(store.isDownloaded(BuiltInAudioCatalog.AUDIO_ID, 39, 1))
        assertNull(store.localUri(BuiltInAudioCatalog.AUDIO_ID, 39, 1))
    }

    @Test
    fun `publishing rejects an empty or unrelated temp file`() {
        val empty = store.tempFile(BuiltInAudioCatalog.AUDIO_ID, 0, 2)
        empty.writeBytes(byteArrayOf())
        assertFails { store.publishTemp(BuiltInAudioCatalog.AUDIO_ID, 0, 2, empty) }

        val unrelated = File(temporaryFolder.root, "unrelated.part").apply {
            writeBytes("ID3audio".toByteArray())
        }
        assertFails { store.publishTemp(BuiltInAudioCatalog.AUDIO_ID, 0, 2, unrelated) }
    }

    @Test
    fun `coordinates and audio IDs cannot escape the fixed directory`() {
        assertFails { store.localFile("../outside", 0, 1) }
        assertFails { store.localFile(BuiltInAudioCatalog.AUDIO_ID, -1, 1) }
        assertFails { store.localFile(BuiltInAudioCatalog.AUDIO_ID, 66, 1) }
        assertFails { store.localFile(BuiltInAudioCatalog.AUDIO_ID, 0, 0) }
        assertFails { store.localFile(BuiltInAudioCatalog.AUDIO_ID, 0, 151) }
    }

    @Test
    fun `remove deletes a published chapter and is idempotent`() {
        val temp = store.tempFile(BuiltInAudioCatalog.AUDIO_ID, 65, 22)
        temp.writeBytes("ID3audio".toByteArray())
        store.publishTemp(BuiltInAudioCatalog.AUDIO_ID, 65, 22, temp)

        assertTrue(store.remove(BuiltInAudioCatalog.AUDIO_ID, 65, 22))
        assertFalse(store.isDownloaded(BuiltInAudioCatalog.AUDIO_ID, 65, 22))
        assertFalse(store.remove(BuiltInAudioCatalog.AUDIO_ID, 65, 22))
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
