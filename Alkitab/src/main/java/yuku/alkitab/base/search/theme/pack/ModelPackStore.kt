package yuku.alkitab.base.search.theme.pack

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

sealed interface ModelPackState {
    data object Absent : ModelPackState
    data class Downloading(val bytes: Long, val total: Long) : ModelPackState
    data class Ready(val model: File, val tokenizer: File) : ModelPackState
    data class Failed(val reason: String) : ModelPackState
}

/** Versioned, app-private storage with a final marker as the publication boundary. */
class ModelPackStore(root: File) {
    private val packsRoot = File(root, "offline-search/packs")

    constructor(context: Context) : this(context.filesDir)

    fun inspect(manifest: ModelPackManifest): ModelPackState {
        val directory = revisionDirectory(manifest)
        val marker = File(directory, READY_MARKER)
        val model = finalFile(manifest, manifest.model)
        val tokenizer = finalFile(manifest, manifest.tokenizer)
        return if (
            marker.isFile && marker.readText() == manifest.revision &&
            model.isFile && model.length() == manifest.model.length &&
            tokenizer.isFile && tokenizer.length() == manifest.tokenizer.length
        ) {
            ModelPackState.Ready(model, tokenizer)
        } else {
            ModelPackState.Absent
        }
    }

    fun stagingFile(manifest: ModelPackManifest, artifact: ModelPackArtifact): File =
        checkedArtifactFile(manifest, artifact, ".part")

    fun publish(manifest: ModelPackManifest) {
        val artifacts = listOf(manifest.model, manifest.tokenizer)
        artifacts.forEach { artifact ->
            val staged = stagingFile(manifest, artifact)
            require(staged.isFile) { "Missing staged artifact: ${artifact.fileName}" }
            require(staged.length() == artifact.length) { "Length mismatch: ${artifact.fileName}" }
            require(sha256(staged) == artifact.sha256.lowercase()) { "Checksum mismatch: ${artifact.fileName}" }
        }

        val directory = revisionDirectory(manifest)
        check(directory.mkdirs() || directory.isDirectory)
        val marker = File(directory, READY_MARKER)
        marker.delete()
        artifacts.forEach { artifact ->
            atomicMove(stagingFile(manifest, artifact), finalFile(manifest, artifact))
        }
        val markerPart = File(directory, "$READY_MARKER.part")
        markerPart.writeText(manifest.revision)
        atomicMove(markerPart, marker)
    }

    fun remove(manifest: ModelPackManifest): Boolean {
        val directory = revisionDirectory(manifest)
        if (!directory.exists()) return false
        return directory.deleteRecursively()
    }

    fun discardStaging(manifest: ModelPackManifest) {
        stagingFile(manifest, manifest.model).delete()
        stagingFile(manifest, manifest.tokenizer).delete()
        File(revisionDirectory(manifest), "$READY_MARKER.part").delete()
    }

    private fun revisionDirectory(manifest: ModelPackManifest): File {
        require(manifest.revision.matches(Regex("[0-9a-f]{40}"))) { "Invalid model revision" }
        return File(packsRoot, manifest.revision)
    }

    private fun finalFile(manifest: ModelPackManifest, artifact: ModelPackArtifact): File =
        checkedArtifactFile(manifest, artifact, "")

    private fun checkedArtifactFile(
        manifest: ModelPackManifest,
        artifact: ModelPackArtifact,
        suffix: String,
    ): File {
        require(artifact === manifest.model || artifact === manifest.tokenizer) { "Unknown pack artifact" }
        require(artifact.fileName.matches(Regex("[A-Za-z0-9._-]+"))) { "Unsafe artifact file name" }
        return File(revisionDirectory(manifest), artifact.fileName + suffix)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun atomicMove(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val READY_MARKER = "READY"
    }
}
