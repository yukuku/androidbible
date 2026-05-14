package yuku.alkitab.base.util

import android.app.DownloadManager
import android.content.Intent
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.AlertDialogActivity
import yuku.alkitab.base.br.VersionDownloadCompleteReceiver
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.debug.R

/**
 * In-process tracker for Bible version downloads, backed by WorkManager.
 *
 * Each in-flight download is represented by a [Row] keyed both by a stable string
 * [Row.key] (e.g. `"version:preset_name:TB"`) and by an internal int [Row.id] that
 * we allocate ourselves. The int id is what the rest of the app (notably
 * [VersionDownloadCompleteReceiver]) uses to identify a download; it preserves the
 * public surface of the previous PRDownloader-based implementation.
 *
 * Lifecycles:
 * - [enqueue] creates a [androidx.work.OneTimeWorkRequest] for [VersionDownloadWorker],
 *   registers the [Row], and launches an observer coroutine that mirrors WorkInfo
 *   state into [Row.dmStatus] (using Android `DownloadManager.STATUS_*` constants to
 *   keep the existing UI checks in `VersionListFragment` working unchanged).
 * - The observer also pumps progress data into the row and emits a throttled
 *   `versionListReload` so the list view re-renders.
 * - On terminal `SUCCEEDED`, the observer hands off to
 *   [VersionDownloadCompleteReceiver.onReceive], which finalises the file on a
 *   background thread and then calls [remove] in its `finally`.
 * - On terminal `FAILED`, the observer shows an error dialog and calls [remove].
 * - [remove] cancels both the observer coroutine and the underlying WorkManager
 *   work, then strips the row from the maps.
 */
class DownloadMapper private constructor() {

    private class Row(
        @JvmField val id: Int,
        @JvmField val key: String,
        @JvmField val title: String,
        @JvmField val destPath: String,
        @JvmField val workId: UUID,
        @JvmField val attrs: Map<String, String>,
    ) {
        @JvmField var currentBytes: Long = 0L
        @JvmField var totalBytes: Long = 0L
        @JvmField var dmStatus: Int = DownloadManager.STATUS_PENDING
        @JvmField var previouslyUpdatedProgressTime: Long = 0L
        @JvmField var observerJob: Job? = null
    }

    private val nextId = AtomicInteger(1)
    private val currentByKey = mutableMapOf<String, Row>()
    private val currentById = mutableMapOf<Int, Row>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getStatus(downloadKey: String): Int = synchronized(this) {
        currentByKey[downloadKey]?.dmStatus ?: 0
    }

    fun getStatus(id: Int): Int = synchronized(this) {
        currentById[id]?.dmStatus ?: 0
    }

    fun getDownloadProgress(downloadKey: String): Float = synchronized(this) {
        val row = currentByKey[downloadKey] ?: return -1f
        if (row.totalBytes <= 0L) return -1f
        row.currentBytes.toFloat() / row.totalBytes.toFloat()
    }

    /** Must be called only after verifying that this id exists. */
    fun getAttrs(id: Int): Map<String, String>? = synchronized(this) {
        currentById[id]?.attrs
    }

    /** Must be called only after verifying that this id exists. */
    fun getDownloadedFilePath(id: Int): String? = synchronized(this) {
        currentById[id]?.destPath
    }

    fun enqueue(downloadKey: String, url: String, title: String, attrs: Map<String, String>) {
        val dir = downloadTempDirPath()
        val name = downloadTempBasename(downloadKey)
        val destPath = File(dir, name).absolutePath

        val request = OneTimeWorkRequest.Builder(VersionDownloadWorker::class.java)
            .setInputData(workDataOf(
                VersionDownloadWorker.KEY_URL to url,
                VersionDownloadWorker.KEY_DEST_PATH to destPath,
            ))
            .addTag(WORK_TAG)
            .build()

        val workManager = WorkManager.getInstance(App.context)
        workManager.enqueue(request)

        val id = nextId.getAndIncrement()
        val row = Row(
            id = id,
            key = downloadKey,
            title = title,
            destPath = destPath,
            workId = request.id,
            attrs = LinkedHashMap(attrs),
        )

        synchronized(this) {
            currentByKey[downloadKey] = row
            currentById[id] = row
        }

        // scope uses Dispatchers.Main and enqueue() is always invoked from the main
        // thread (UI click handlers), so this launch is guaranteed to run *after*
        // enqueue() returns — no race with the maps above.
        row.observerJob = scope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info == null) {
                    silentlyDrop(row)
                    return@collect
                }
                when (info.state) {
                    WorkInfo.State.ENQUEUED -> row.dmStatus = DownloadManager.STATUS_PENDING

                    WorkInfo.State.RUNNING -> {
                        row.dmStatus = DownloadManager.STATUS_RUNNING
                        updateProgressFrom(row, info.progress)
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        if (row.dmStatus != DownloadManager.STATUS_SUCCESSFUL) {
                            row.dmStatus = DownloadManager.STATUS_SUCCESSFUL
                            VersionDownloadCompleteReceiver.onReceive(row.id)
                        }
                    }

                    WorkInfo.State.FAILED -> {
                        if (row.dmStatus != DownloadManager.STATUS_FAILED) {
                            row.dmStatus = DownloadManager.STATUS_FAILED
                            handleFailure(row, info.outputData)
                        }
                    }

                    WorkInfo.State.CANCELLED -> silentlyDrop(row)

                    else -> Unit
                }
            }
        }

        AppEvents.emitVersionListReload()
    }

    private fun updateProgressFrom(row: Row, progressData: Data) {
        val current = progressData.getLong(VersionDownloadWorker.KEY_CURRENT_BYTES, -1L)
        val total = progressData.getLong(VersionDownloadWorker.KEY_TOTAL_BYTES, -1L)
        if (current < 0L) return
        row.currentBytes = current
        if (total >= 0L) row.totalBytes = total

        val now = System.currentTimeMillis()
        if (row.previouslyUpdatedProgressTime == 0L || now - row.previouslyUpdatedProgressTime > PROGRESS_NOTIFY_INTERVAL_MS) {
            row.previouslyUpdatedProgressTime = now
            AppEvents.emitVersionListReload()
        }
    }

    private fun handleFailure(row: Row, outputData: Data) {
        val errorType = outputData.getString(VersionDownloadWorker.KEY_ERROR_TYPE)
            ?: VersionDownloadWorker.ERROR_UNKNOWN
        val message = outputData.getString(VersionDownloadWorker.KEY_ERROR_MESSAGE) ?: errorType
        AppLog.e(TAG, "@@onError: downloadKey=${row.key} type=$errorType message=$message")

        val msg: CharSequence = when (errorType) {
            VersionDownloadWorker.ERROR_CONNECTION,
            VersionDownloadWorker.ERROR_CANCELLED ->
                TextUtils.expandTemplate(App.context.getString(R.string.version_download_network_error), row.title)

            VersionDownloadWorker.ERROR_STORAGE ->
                App.context.getString(R.string.version_download_saving_io_error)

            else ->
                TextUtils.expandTemplate(App.context.getString(R.string.version_download_server_error), row.title)
        }

        App.context.startActivity(
            AlertDialogActivity.createOkIntent(null, msg.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        remove(row.id)
        AppEvents.emitVersionListReload()
    }

    private fun silentlyDrop(row: Row) {
        synchronized(this) {
            currentByKey.remove(row.key)
            currentById.remove(row.id)
        }
        row.observerJob?.cancel()
    }

    /** Stop and remove from in-memory list. */
    fun remove(id: Int) {
        val row: Row? = synchronized(this) {
            val r = currentById[id]
            if (r != null) {
                currentByKey.remove(r.key)
                currentById.remove(r.id)
            }
            r
        }
        if (row != null) {
            row.observerJob?.cancel()
            WorkManager.getInstance(App.context).cancelWorkById(row.workId)
        }
    }

    /**
     * Called by [VersionDownloadCompleteReceiver] once it has finished consuming
     * the downloaded temp file. Deletes the temp file and removes the in-memory
     * row.
     *
     * Why this exists: the temp file path is derived deterministically from the
     * download key, so leaving the file behind would make the next download with
     * the same key (typically a version *update*) see `existingBytes > 0` in
     * [VersionDownloadWorker] and try to resume from a stale offset against a
     * different file. The server then either replies 206 (corrupting the result
     * with a frankenstein of the old prefix and the new suffix) or 416 if the
     * new file is smaller, which surfaces to the user as a "Cannot connect to
     * server" generic error.
     *
     * Does not call `cancelWorkById`: the caller has already observed terminal
     * `SUCCEEDED` state, so the cancellation would be a no-op anyway.
     */
    fun consumeAndRemove(id: Int) {
        val row: Row? = synchronized(this) {
            val r = currentById[id]
            if (r != null) {
                currentByKey.remove(r.key)
                currentById.remove(r.id)
            }
            r
        }
        if (row != null) {
            row.observerJob?.cancel()
            @Suppress("ResultOfMethodCallIgnored")
            File(row.destPath).delete()
        }
    }

    /**
     * Test seam: inject a row directly without going through [enqueue] (which
     * would require WorkManager initialization and an actual HTTP fetch). Tests
     * use this to exercise the post-download lifecycle ([consumeAndRemove],
     * [remove]) against a controlled temp-file path.
     */
    @VisibleForTesting
    internal fun seedRowForTest(downloadKey: String, destPath: String): Int {
        val id = nextId.getAndIncrement()
        val row = Row(
            id = id,
            key = downloadKey,
            title = "test",
            destPath = destPath,
            workId = UUID.randomUUID(),
            attrs = emptyMap(),
        )
        synchronized(this) {
            currentByKey[downloadKey] = row
            currentById[id] = row
        }
        return id
    }

    private fun downloadTempDirPath(): String {
        val res = File(App.context.cacheDir, "DownloadMapper-tmp")
        res.mkdirs()
        return res.absolutePath
    }

    private fun downloadTempBasename(downloadKey: String): String {
        val safe = StringBuilder(downloadKey.length + 40)
        for (c in downloadKey) {
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9') safe.append(c)
        }
        return "DownloadMapper-$safe${downloadKey.hashCode()}.tmp"
    }

    companion object {
        private const val TAG = "DownloadMapper"
        private const val WORK_TAG = "VersionDownload"
        private const val PROGRESS_NOTIFY_INTERVAL_MS = 250L

        @JvmField
        val instance = DownloadMapper()
    }
}
