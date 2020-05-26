package yuku.alkitab.base.util

import yuku.afw.storage.Preferences
import yuku.alkitab.base.S
import yuku.alkitab.base.model.SyncShadow
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.sync.Sync
import yuku.alkitab.base.sync.Sync_History
import yuku.alkitab.debug.BuildConfig
import java.util.Locale

private const val TAG = "HistorySyncUtil"

object HistorySyncUtil {
    /**
     * Makes the current history updated with patches (append delta) from server.
     * Also updates the shadow (both data and the revno).
     *
     * @return [Sync.ApplyAppendDeltaResult.ok] if history and sync shadow are updated. Otherwise else.
     */
    @JvmStatic
    fun applyHistoryAppendDelta(
        history: History,
        final_revno: Int,
        append_delta: Sync.Delta<Sync_History.Content>,
        entitiesBeforeSync: List<Sync.Entity<Sync_History.Content>>,
        simpleTokenBeforeSync: String
    ): Sync.ApplyAppendDeltaResult {
        val entriesCopy = history.listAllEntries().toMutableList()
        Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_HISTORY, true)
        return try {
            // if the current entities are not the same as the ones had when contacting server, reject this append delta.
            if (!Sync.entitiesEqual(Sync_History.getEntitiesFromCurrent(), entitiesBeforeSync)) {
                return Sync.ApplyAppendDeltaResult.dirty_entities
            }

            // if the current simpleToken has changed (sync user logged off or changed), reject this append delta
            if (simpleTokenBeforeSync != Preferences.getString(Prefkey.sync_simpleToken)) {
                return Sync.ApplyAppendDeltaResult.dirty_sync_account
            }

            for (o in append_delta.operations) {
                when (o.opkind) {
                    Sync.Opkind.del -> deleteByGid(entriesCopy, o.gid)
                    Sync.Opkind.add, Sync.Opkind.mod -> addOrModByGid(entriesCopy, o.gid, o.content, o.creator_id)
                    null -> {
                        // should not happen
                    }
                }
            }

            // sort by timestamp desc
            entriesCopy.sortByDescending { it.timestamp }

            // commit changes
            history.replaceAllEntries(entriesCopy)

            // if we reach here, the local database has been updated with the append delta.
            val ss = Sync_History.shadowFromEntities(Sync_History.getEntitiesFromCurrent(), final_revno)
            S.getDb().insertOrUpdateSyncShadowBySyncSetName(ss)
            history.save()

            // when debugging, print
            if (BuildConfig.DEBUG) {
                AppLog.d(TAG, "After sync, the history entries are:")
                AppLog.d(TAG, String.format(Locale.US, "  ari ====   timestamp ===============   %-40s   %-40s", "gid", "creator_id"))
                for ((gid, ari, timestamp, creator_id) in history.listAllEntries()) {
                    AppLog.d(TAG, String.format(Locale.US, "- 0x%06x   %tF %<tT %<tz   %-40s   %-40s", ari, timestamp, gid, creator_id))
                }
            }

            Sync.ApplyAppendDeltaResult.ok
        } finally {
            Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_HISTORY, false)
        }
    }

    private fun deleteByGid(
        entries: MutableList<History.Entry>,
        gid: String
    ) {
        for (i in entries.indices) {
            if (entries[i].gid == gid) {
                // delete and exit
                entries.removeAt(i)
                return
            }
        }
    }

    private fun addOrModByGid(
        entries: MutableList<History.Entry>,
        gid: String,
        content: Sync_History.Content,
        creator_id: String
    ) {
        val newEntry = History.Entry(
            gid = gid,
            ari = content.ari ?: 0,
            timestamp = content.timestamp ?: 0L,
            creator_id = creator_id
        )
        for (i in entries.indices) {
            if (entries[i].gid == gid) {
                // update!
                entries[i] = newEntry
                return
            }
        }

        // not found, create new one
        entries.add(newEntry)
    }
}
