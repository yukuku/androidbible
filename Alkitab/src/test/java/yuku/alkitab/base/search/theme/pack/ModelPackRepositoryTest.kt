package yuku.alkitab.base.search.theme.pack

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPackRepositoryTest {
    private val manifest = ModelPackManifest(
        schema = 1,
        modelId = "model",
        revision = "a".repeat(40),
        model = ModelPackArtifact("model.onnx", "https://example.test/model", 1, "00"),
        tokenizer = ModelPackArtifact("tokenizer.json", "https://example.test/tokenizer", 1, "00"),
    )

    @Test
    fun `install replaces completed unique work so a removed pack can be installed again`() {
        val workManager = mockk<WorkManager>(relaxed = true) {
            every { getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
        }
        val repository = ModelPackRepository(workManager, mockk(relaxed = true), manifest)

        repository.install()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                ModelPackRepository.workName(manifest),
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `remove immediately refreshes observed state`() = runBlocking {
        val workManager = mockk<WorkManager>(relaxed = true) {
            every { getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
        }
        var installed = true
        val store = mockk<ModelPackStore> {
            every { inspect(manifest) } answers {
                if (installed) ModelPackState.Ready(File("model"), File("tokenizer")) else ModelPackState.Absent
            }
            every { remove(manifest) } answers {
                installed = false
                true
            }
        }
        val repository = ModelPackRepository(workManager, store, manifest)
        val observed = mutableListOf<ModelPackState>()
        val ready = CompletableDeferred<Unit>()
        val collection = launch {
            repository.state.take(2).collect {
                observed += it
                if (observed.size == 1) ready.complete(Unit)
            }
        }

        withTimeout(1_000) { ready.await() }
        repository.remove()
        withTimeout(1_000) { collection.join() }

        assertEquals(listOf(ModelPackState.Ready(File("model"), File("tokenizer")), ModelPackState.Absent), observed)
    }
}
