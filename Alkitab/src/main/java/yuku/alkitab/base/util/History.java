package yuku.alkitab.base.util;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.sync.Sync_History;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.model.util.Gid;

public class History {
	static final String TAG = History.class.getSimpleName();

	private static final int MAX_HISTORY_ENTRIES = 20;

	@Keep
	public static class HistoryEntry {
		public String gid;
		public int ari;
		public long timestamp;
		public String creator_id;

		public static HistoryEntry createEmptyEntry() {
			return new HistoryEntry();
		}
	}

	@Keep
	static class HistoryJson {
		public List<HistoryEntry> entries;
	}

	final List<HistoryEntry> entries;

	private static History instance;

	@NonNull
	public static History getInstance() {
		if (instance == null) {
			instance = new History();
		}
		return instance;
	}

	private History() {
		entries = new ArrayList<>();

		final String s = Preferences.getString(Prefkey.history);
		if (s != null) {
			final HistoryJson obj = App.getDefaultGson().fromJson(s, HistoryJson.class);
			entries.addAll(obj.entries);
		}
	}

	public synchronized void save() {
		final HistoryJson obj = new HistoryJson();
		obj.entries = this.entries;
		final String new_json = App.getDefaultGson().toJson(obj);

		final String old_json = Preferences.getString(Prefkey.history);
		if (!U.equals(old_json, new_json)) {
			Preferences.setString(Prefkey.history, new_json);
			Sync.notifySyncNeeded(SyncShadow.SYNC_SET_HISTORY);
		} else {
			AppLog.d(TAG, "History not changed.");
		}
	}

	public synchronized void add(int ari) {
		add(ari, System.currentTimeMillis());
	}

	synchronized void add(int ari, long timestamp) {
		// check: do we have this previously?
		for (int i = entries.size() - 1; i >= 0; i--) {
			final HistoryEntry entry = entries.get(i);
			if (entry.ari == ari) {
				// YES. Remove this.
				entries.remove(i);
			}
		}

		// Add it to the front
		final HistoryEntry entry = new HistoryEntry();
		entry.gid = Gid.newGid();
		entry.ari = ari;
		entry.timestamp = timestamp;
		entry.creator_id = InstallationUtil.getInstallationId();
		entries.add(0, entry);

		// and remove if overflow
		while (entries.size() > MAX_HISTORY_ENTRIES) {
			entries.remove(MAX_HISTORY_ENTRIES);
		}
	}

	public synchronized int getSize() {
		return entries.size();
	}

	public synchronized int getAri(final int position) {
		return entries.get(position).ari;
	}

	public synchronized long getTimestamp(final int position) {
		return entries.get(position).timestamp;
	}

	public synchronized String getCreatorId(final int position) {
		return entries.get(position).creator_id;
	}

	public List<HistoryEntry> listAllEntries() {
		return new ArrayList<>(entries);
	}


	/**
	 * Makes the current history updated with patches (append delta) from server.
	 * Also updates the shadow (both data and the revno).
	 * @return {@link yuku.alkitab.base.sync.Sync.ApplyAppendDeltaResult#ok} if history and sync shadow are updated. Otherwise else.
	 */
	@NonNull public Sync.ApplyAppendDeltaResult applyHistoryAppendDelta(final int final_revno, @NonNull final Sync.Delta<Sync_History.Content> append_delta, @NonNull final List<Sync.Entity<Sync_History.Content>> entitiesBeforeSync, @NonNull final String simpleTokenBeforeSync) {
		final ArrayList<HistoryEntry> entriesCopy = new ArrayList<>(entries);

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
			this.entries.clear();
			this.entries.addAll(new ArrayList<>(entriesCopy));

			// if we reach here, the local database has been updated with the append delta.
			final SyncShadow ss = Sync_History.shadowFromEntities(Sync_History.getEntitiesFromCurrent(), final_revno);
			S.getDb().insertOrUpdateSyncShadowBySyncSetName(ss);
			this.save();

			// when debugging, print
			if (BuildConfig.DEBUG) {
				AppLog.d(TAG, "After sync, the history entries are:");
				AppLog.d(TAG, String.format(Locale.US, "  ari ====   timestamp ===============   %-40s   %-40s", "gid", "creator_id"));
				for (final HistoryEntry entry : entries) {
					AppLog.d(TAG, String.format(Locale.US, "- 0x%06x   %tF %<tT %<tz   %-40s   %-40s", entry.ari, entry.timestamp, entry.gid, entry.creator_id));
				}
			}

			return Sync.ApplyAppendDeltaResult.ok;
		} finally {
			Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_HISTORY, false);
		}
	}

	private static void deleteByGid(@NonNull final ArrayList<HistoryEntry> entries, @NonNull final String gid) {
		for (int i = 0; i < entries.size(); i++) {
			final HistoryEntry entry = entries.get(i);
			if (U.equals(entry.gid, gid)) {
				// delete and exit
				entries.remove(i);
				return;
			}
		}
	}

	private static void addOrModByGid(@NonNull final ArrayList<HistoryEntry> entries, @NonNull final String gid, @NonNull final Sync_History.Content content, @NonNull final String creator_id) {
		for (int i = 0; i < entries.size(); i++) {
			final HistoryEntry entry = entries.get(i);
			if (U.equals(entry.gid, gid)) {
				// update!
				entry.ari = content.ari;
				entry.timestamp = content.timestamp;
				entry.creator_id = creator_id;
				return;
			}
		}

		// not found, create new one
		final HistoryEntry entry = HistoryEntry.createEmptyEntry();

		entry.gid = gid;
		entry.ari = content.ari;
		entry.timestamp = content.timestamp;
		entry.creator_id = creator_id;

		entries.add(entry);
	}
}

