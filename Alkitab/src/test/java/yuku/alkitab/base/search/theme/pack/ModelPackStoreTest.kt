package yuku.alkitab.base.search.theme.pack

import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPackStoreTest {
    @Test
    fun `valid staged artifacts publish as one compatible pack`() {
        val root = Files.createTempDirectory("model-pack").toFile()
        val model = "model".toByteArray()
        val tokenizer = "tokenizer".toByteArray()
        val manifest = manifest(model, tokenizer)
        val store = ModelPackStore(root)
        store.stagingFile(manifest, manifest.model).apply { parentFile!!.mkdirs(); writeBytes(model) }
        store.stagingFile(manifest, manifest.tokenizer).writeBytes(tokenizer)

        store.publish(manifest)

        val ready = store.inspect(manifest)
        assertTrue(ready is ModelPackState.Ready)
        ready as ModelPackState.Ready
        assertEquals(model.toList(), ready.model.readBytes().toList())
        assertEquals(tokenizer.toList(), ready.tokenizer.readBytes().toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hash mismatch is rejected without a ready marker`() {
        val root = Files.createTempDirectory("model-pack-bad").toFile()
        val manifest = manifest("model".toByteArray(), "tokenizer".toByteArray())
        val store = ModelPackStore(root)
        store.stagingFile(manifest, manifest.model).apply { parentFile!!.mkdirs(); writeText("wrong") }
        store.stagingFile(manifest, manifest.tokenizer).writeText("tokenizer")

        try {
            store.publish(manifest)
        } finally {
            assertEquals(ModelPackState.Absent, store.inspect(manifest))
        }
    }

    @Test
    fun `remove deletes only the pinned revision directory`() {
        val root = Files.createTempDirectory("model-pack-remove").toFile()
        val model = "model".toByteArray()
        val tokenizer = "tokenizer".toByteArray()
        val manifest = manifest(model, tokenizer)
        val store = ModelPackStore(root)
        store.stagingFile(manifest, manifest.model).apply { parentFile!!.mkdirs(); writeBytes(model) }
        store.stagingFile(manifest, manifest.tokenizer).writeBytes(tokenizer)
        store.publish(manifest)

        store.remove(manifest)

        assertEquals(ModelPackState.Absent, store.inspect(manifest))
    }

    private fun manifest(model: ByteArray, tokenizer: ByteArray) = ModelPackManifest(
        schema = 1,
        modelId = "test/model",
        revision = "a".repeat(40),
        model = artifact("model.onnx", model),
        tokenizer = artifact("tokenizer.json", tokenizer),
    )

    private fun artifact(name: String, bytes: ByteArray) = ModelPackArtifact(
        fileName = name,
        url = "https://huggingface.co/test/$name",
        length = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
    )
}
