package yuku.alkitab.base.util;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.sync.Sync;
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
}

