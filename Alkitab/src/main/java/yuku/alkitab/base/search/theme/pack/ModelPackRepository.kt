package yuku.alkitab.base.search.theme.pack

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelPackRepository(
    private val workManager: WorkManager,
    private val store: ModelPackStore,
    private val manifest: ModelPackManifest,
) {
    constructor(context: Context) : this(
        WorkManager.getInstance(context.applicationContext),
        ModelPackStore(context.applicationContext),
        ModelPackManifest.load(context.applicationContext),
    )

    val state: Flow<ModelPackState> = workManager.getWorkInfosForUniqueWorkFlow(workName(manifest)).map { infos ->
        when (val latest = infos.lastOrNull()) {
            null -> store.inspect(manifest)
            else -> when (latest.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING ->
                    ModelPackState.Downloading(
                        latest.progress.getLong(ModelPackDownloadWorker.KEY_BYTES, 0),
                        latest.progress.getLong(ModelPackDownloadWorker.KEY_TOTAL, manifest.model.length + manifest.tokenizer.length),
                    )
                WorkInfo.State.SUCCEEDED -> store.inspect(manifest)
                WorkInfo.State.FAILED -> ModelPackState.Failed(latest.outputData.getString(ModelPackDownloadWorker.KEY_ERROR) ?: "download")
                WorkInfo.State.CANCELLED -> store.inspect(manifest)
            }
        }
    }

    fun install() {
        val request = OneTimeWorkRequestBuilder<ModelPackDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(manifest), ExistingWorkPolicy.KEEP, request)
    }

    fun readyPack(): ModelPackState.Ready? = store.inspect(manifest) as? ModelPackState.Ready

    fun remove(): Boolean {
        workManager.cancelUniqueWork(workName(manifest))
        return store.remove(manifest)
    }

    companion object {
        internal fun workName(manifest: ModelPackManifest) = "offline-search-pack:${manifest.revision}"
    }
}
