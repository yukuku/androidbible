package yuku.alkitab.base.audio.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface DownloadState {
    data object NotDownloaded : DownloadState
    data class Downloading(val bytes: Long, val total: Long) : DownloadState
    data object Downloaded : DownloadState
    data object Failed : DownloadState
}

/** Schedules and observes unique per-chapter offline audio work. */
class AudioChapterDownloads(
    private val workManager: WorkManager,
    private val store: AudioChapterDownloadStore,
) {

    constructor(context: Context) : this(
        WorkManager.getInstance(context.applicationContext),
        AudioChapterDownloadStore(context.applicationContext),
    )

    fun enqueue(audioId: String, bookId: Int, chapter1: Int, url: String) {
        val request = OneTimeWorkRequestBuilder<AudioChapterDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(
                workDataOf(
                    AudioChapterDownloadWorker.KEY_AUDIO_ID to audioId,
                    AudioChapterDownloadWorker.KEY_BOOK_ID to bookId,
                    AudioChapterDownloadWorker.KEY_CHAPTER_1 to chapter1,
                    AudioChapterDownloadWorker.KEY_URL to url,
                ),
            )
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(audioId, bookId, chapter1), ExistingWorkPolicy.KEEP, request)
    }

    fun workFlow(audioId: String, bookId: Int, chapter1: Int): Flow<DownloadState> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(audioId, bookId, chapter1)).map { infos ->
            val latest = infos.lastOrNull()
            when (latest?.state) {
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED,
                WorkInfo.State.RUNNING,
                -> DownloadState.Downloading(
                    latest.progress.getLong(AudioChapterDownloadWorker.KEY_CURRENT_BYTES, 0L),
                    latest.progress.getLong(AudioChapterDownloadWorker.KEY_TOTAL_BYTES, -1L),
                )
                WorkInfo.State.SUCCEEDED -> if (store.isDownloaded(audioId, bookId, chapter1)) {
                    DownloadState.Downloaded
                } else {
                    DownloadState.Failed
                }
                WorkInfo.State.FAILED -> DownloadState.Failed
                WorkInfo.State.CANCELLED, null -> currentFileState(audioId, bookId, chapter1)
            }
        }

    fun remove(audioId: String, bookId: Int, chapter1: Int): Boolean {
        workManager.cancelUniqueWork(workName(audioId, bookId, chapter1))
        return store.remove(audioId, bookId, chapter1)
    }

    private fun currentFileState(audioId: String, bookId: Int, chapter1: Int): DownloadState =
        if (store.isDownloaded(audioId, bookId, chapter1)) DownloadState.Downloaded else DownloadState.NotDownloaded

    companion object {
        internal fun workName(audioId: String, bookId: Int, chapter1: Int): String =
            "audio:$audioId:$bookId:$chapter1"
    }
}
