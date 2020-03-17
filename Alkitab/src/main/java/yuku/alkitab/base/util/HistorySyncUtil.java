package yuku.alkitab.base.util;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.sync.Sync_History;
import yuku.alkitab.debug.BuildConfig;

public class HistorySyncUtil {
    static final String TAG = HistorySyncUtil.class.getSimpleName();

    /**
     * Makes the current history updated with patches (append delta) from server.
     * Also updates the shadow (both data and the revno).
     *
     * @return {@link yuku.alkitab.base.sync.Sync.ApplyAppendDeltaResult#ok} if history and sync shadow are updated. Otherwise else.
     */
    @NonNull
    public static Sync.ApplyAppendDeltaResult applyHistoryAppendDelta(@NonNull final History history, final int final_revno, @NonNull final Sync.Delta<Sync_History.Content> append_delta, @NonNull final List<Sync.Entity<Sync_History.Content>> entitiesBeforeSync, @NonNull final String simpleTokenBeforeSync) {
        final ArrayList<History.HistoryEntry> entriesCopy = new ArrayList<>(history.getEntries());

        Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_HISTORY, true);
        try {
            { // if the current entities are not the same as the ones had when contacting server, reject this append delta.
                final List<Sync.Entity<Sync_History.Content>> currentEntities = Sync_History.getEntitiesFromCurrent();
                if (!Sync.entitiesEqual(currentEntities, entitiesBeforeSync)) {
                    return Sync.ApplyAppendDeltaResult.dirty_entities;
                }
            }

            { // if the current simpleToken has changed (sync user logged off or changed), reject this append delta
                final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
                if (!simpleTokenBeforeSync.equals(simpleToken)) {
                    return Sync.ApplyAppendDeltaResult.dirty_sync_account;
                }
            }

            for (final Sync.Operation<Sync_History.Content> o : append_delta.operations) {
                switch (o.opkind) {
                    case del:
                        deleteByGid(entriesCopy, o.gid);
                        break;
                    case add:
                    case mod:
                        addOrModByGid(entriesCopy, o.gid, o.content, o.creator_id);
                        break;
                }
            }

            // sort by timestamp desc
            Collections.sort(entriesCopy, (a, b) -> (a.timestamp < b.timestamp) ? +1 : ((a.timestamp > b.timestamp) ? -1 : 0));

            // commit changes
            history.getEntries().clear();
            history.getEntries().addAll(new ArrayList<>(entriesCopy));

            // if we reach here, the local database has been updated with the append delta.
            final SyncShadow ss = Sync_History.shadowFromEntities(Sync_History.getEntitiesFromCurrent(), final_revno);
            S.getDb().insertOrUpdateSyncShadowBySyncSetName(ss);
            history.save();

            // when debugging, print
            if (BuildConfig.DEBUG) {
                AppLog.d(TAG, "After sync, the history entries are:");
                AppLog.d(TAG, String.format(Locale.US, "  ari ====   timestamp ===============   %-40s   %-40s", "gid", "creator_id"));
                for (final History.HistoryEntry entry : history.getEntries()) {
                    AppLog.d(TAG, String.format(Locale.US, "- 0x%06x   %tF %<tT %<tz   %-40s   %-40s", entry.ari, entry.timestamp, entry.gid, entry.creator_id));
                }
            }

            return Sync.ApplyAppendDeltaResult.ok;
        } finally {
            Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_HISTORY, false);
        }
    }

    static void deleteByGid(@NonNull final ArrayList<History.HistoryEntry> entries, @NonNull final String gid) {
        for (int i = 0; i < entries.size(); i++) {
            final History.HistoryEntry entry = entries.get(i);
            if (U.equals(entry.gid, gid)) {
                // delete and exit
                entries.remove(i);
                return;
            }
        }
    }

    static void addOrModByGid(@NonNull final ArrayList<History.HistoryEntry> entries, @NonNull final String gid, @NonNull final Sync_History.Content content, @NonNull final String creator_id) {
        for (int i = 0; i < entries.size(); i++) {
            final History.HistoryEntry entry = entries.get(i);
            if (U.equals(entry.gid, gid)) {
                // update!
                entries.set(i, new History.HistoryEntry(gid, content.ari, content.timestamp, creator_id));
                return;
            }
        }

        // not found, create new one
        entries.add(new History.HistoryEntry(gid, content.ari, content.timestamp, creator_id));
    }
}
