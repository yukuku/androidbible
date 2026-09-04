package yuku.alkitab.base.audio.download

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import yuku.alkitab.base.audio.builtin.BuiltInAudioCatalog

/** Owns completed and in-progress offline chapter files in app-private storage. */
class AudioChapterDownloadStore(root: File) {

    private val chapterRoot = File(root, "audio/chapters/${BuiltInAudioCatalog.AUDIO_ID}")

    constructor(context: Context) : this(context.filesDir)

    fun localFile(audioId: String, bookId: Int, chapter1: Int): File =
        checkedFile(audioId, bookId, chapter1, suffix = "")

    fun tempFile(audioId: String, bookId: Int, chapter1: Int): File =
        checkedFile(audioId, bookId, chapter1, suffix = ".part").also { file ->
            check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        }

    fun localUri(audioId: String, bookId: Int, chapter1: Int): Uri? {
        val file = localFile(audioId, bookId, chapter1)
        return if (file.isFile && file.length() > MINIMUM_AUDIO_BYTES) Uri.fromFile(file) else null
    }

    fun isDownloaded(audioId: String, bookId: Int, chapter1: Int): Boolean =
        localUri(audioId, bookId, chapter1) != null

    fun publishTemp(audioId: String, bookId: Int, chapter1: Int, temp: File) {
        val expectedTemp = checkedFile(audioId, bookId, chapter1, suffix = ".part")
        require(temp.canonicalFile == expectedTemp.canonicalFile) { "Unexpected audio temp file" }
        require(temp.isFile && temp.length() > MINIMUM_AUDIO_BYTES) { "Audio download is empty" }

        val finalFile = checkedFile(audioId, bookId, chapter1, suffix = "")
        check(finalFile.parentFile!!.mkdirs() || finalFile.parentFile!!.isDirectory)
        Files.move(
            temp.toPath(),
            finalFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    fun remove(audioId: String, bookId: Int, chapter1: Int): Boolean {
        val finalFile = localFile(audioId, bookId, chapter1)
        checkedFile(audioId, bookId, chapter1, suffix = ".part").delete()
        return finalFile.delete()
    }

    private fun checkedFile(audioId: String, bookId: Int, chapter1: Int, suffix: String): File {
        require(audioId == BuiltInAudioCatalog.AUDIO_ID) { "Unknown built-in audio recording" }
        require(bookId in 0..65) { "bookId is outside the 66-book canon" }
        require(chapter1 in 1..150) { "chapter is outside the canonical range" }

        return File(chapterRoot, "%02d/%03d.mp3%s".format(bookId + 1, chapter1, suffix))
    }

    private companion object {
        const val MINIMUM_AUDIO_BYTES = 3L
    }
}
