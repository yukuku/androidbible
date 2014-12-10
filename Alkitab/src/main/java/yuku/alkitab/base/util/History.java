package yuku.alkitab.base.util;

import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.model.util.Gid;

import java.util.ArrayList;
import java.util.List;

public class History {
	private static final int MAX_HISTORY_ENTRIES = 20;

	static class HistoryEntry {
		public String gid;
		public int ari;
		public long timestamp;
		public String creator_id;
	}

	static class HistoryJson {
		public List<HistoryEntry> entries;
	}

	final List<HistoryEntry> entries;

	private static History instance;

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
		final String s = App.getDefaultGson().toJson(obj);
		Preferences.setString(Prefkey.history, s);
	}

	public synchronized void add(int ari) {
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
		entry.timestamp = System.currentTimeMillis();
		entry.creator_id = Sync.getInstallationId();
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
}

