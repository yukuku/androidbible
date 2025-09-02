package yuku.alkitab.base.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import yuku.alkitab.base.App
import yuku.alkitab.base.model.SyncShadow

private const val UNIQUE_WORK_NAME = "sync"

object SyncKotlin {

    @JvmStatic
    @JvmOverloads
    fun syncNow(
        forced: Boolean,
        syncSetNames: Array<String> = SyncShadow.ALL_SYNC_SET_NAMES,
    ) {
        val builder = OneTimeWorkRequestBuilder<SyncAdapter>()
        builder.setInputData(
            workDataOf(
                SyncAdapter.EXTRA_SYNC_SET_NAMES to syncSetNames,
            )
        )
        builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        if (!forced) {
            builder.setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
        }
        val request = builder.build()

        val workManager = WorkManager.getInstance(App.context)

        if (workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get().firstOrNull()?.state == WorkInfo.State.RUNNING) {
            SyncRecorder.log(SyncRecorder.EventKind.worker_is_currently_running, null)
        } else {
            workManager.enqueueUniqueWork(
                uniqueWorkName = UNIQUE_WORK_NAME,
                existingWorkPolicy = ExistingWorkPolicy.REPLACE,
                request = request,
            )
        }
    }
}