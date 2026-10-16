package yuku.alkitab.base.sync

import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
        SyncRecorder.environment(SyncRecorder.EventKind.schedule_requested, null)
        SyncRecorder.log(SyncRecorder.EventKind.diagnostic_stage, null, "stage", "request",
            "forced", forced, "network_required", !forced, "set_count", syncSetNames.size)
        if (syncSetNames.isEmpty()) {
            SyncRecorder.log(SyncRecorder.EventKind.sync_skipped, null, "reason", "empty_request")
            return
        }

        try {
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

            val operation = workManager.enqueueUniqueWork(
                uniqueWorkName = UNIQUE_WORK_NAME,
                existingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE,
                request = request,
            )
            val future = operation.result
            future.addListener({
                try {
                    future.get()
                    SyncRecorder.log(SyncRecorder.EventKind.schedule_accepted, null, "work_id", request.id.toString())
                } catch (error: Exception) {
                    SyncRecorder.error("schedule", null, error)
                }
            }, ContextCompat.getMainExecutor(App.context))
        } catch (error: Exception) {
            SyncRecorder.error("schedule", null, error)
            if (error is InterruptedException) Thread.currentThread().interrupt()
        }
    }

    @JvmStatic
    fun observeWork(owner: LifecycleOwner) {
        try {
            WorkManager.getInstance(App.context).getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
                .observe(owner) { infos ->
                    infos.forEach { info ->
                        SyncRecorder.log(SyncRecorder.EventKind.diagnostic_stage, null, "stage", "schedule",
                            "work_id", info.id.toString(), "state", info.state.name)
                        if (info.outputData.keyValueMap.containsKey(SyncAdapter.EXTRA_IS_SYNC_SUCCESSFUL)) {
                            SyncRecorder.log(SyncRecorder.EventKind.diagnostic_stage, null, "stage", "complete",
                                "work_id", info.id.toString(), "is_sync_successful",
                                info.outputData.getBoolean(SyncAdapter.EXTRA_IS_SYNC_SUCCESSFUL, false))
                        }
                    }
                }
        } catch (error: Exception) {
            SyncRecorder.error("schedule", null, error)
        }
    }

}
