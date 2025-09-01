package yuku.alkitab.base.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import yuku.alkitab.base.App
import yuku.alkitab.base.model.SyncShadow

object SyncKotlin {

    @JvmStatic
    @JvmOverloads
    fun syncNow(syncSetNames: Array<String> = SyncShadow.ALL_SYNC_SET_NAMES) {
        val request = OneTimeWorkRequestBuilder<SyncAdapter>()
            .setInputData(
                workDataOf(
                    SyncAdapter.EXTRA_SYNC_SET_NAMES to syncSetNames,
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(App.context).enqueueUniqueWork(
            uniqueWorkName = "sync",
            existingWorkPolicy = ExistingWorkPolicy.APPEND,
            request = request,
        )
    }
}