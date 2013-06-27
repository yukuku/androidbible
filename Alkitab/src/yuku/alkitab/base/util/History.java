package yuku.alkitab.base.util;

import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import yuku.alkitab.base.App;
import yuku.alkitab.base.syncadapter.SyncUtil;
import yuku.alkitabsync.history.model.client.ClientHistoryEntry;
import yuku.alkitabsync.history.model.common.HistoryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class History {
	private static final String TAG = History.class.getSimpleName();
	private static final String HISTORY_PREFIX = "sejarah/"; //$NON-NLS-1$
	private static final int MAX = 20;
	private static final String FIELD_SEPARATOR_STRING = ":";
	private static final Pattern FIELD_SEPARATOR_PATTERN = Pattern.compile(FIELD_SEPARATOR_STRING);

	final SharedPreferences preferences;
	final List<ClientHistoryEntry> entries;

	private static History instance;

	public static History getInstance() {
		if (instance == null) {
			instance = new History();
		}
		return instance;
	}

	private History() {
		this.preferences = App.getInstantPreferences();

		entries = new ArrayList<ClientHistoryEntry>();

		try {
			int n = preferences.getInt(HISTORY_PREFIX + "n", 0); //$NON-NLS-1$

			final Map<String,?> all = preferences.getAll();

			for (int i = 0; i < n; i++) {
				ClientHistoryEntry entry = new ClientHistoryEntry();
				final Object val = all.get(HISTORY_PREFIX + i);
				if (val instanceof Integer) {
					// for compatibility
					entry.ari = (Integer) val;
					entry.savedInServer = false;
					entry.timestamp = System.currentTimeMillis();
				} else if (val instanceof String) {
					// v1:ari:timestamp:(int)savedinserver
					final String[] splits = FIELD_SEPARATOR_PATTERN.split((String) val);
					entry.ari = Integer.parseInt(splits[1]);
					entry.timestamp = Long.parseLong(splits[2]);
					entry.savedInServer = Integer.parseInt(splits[3]) != 0;
				}
				entries.add(entry);
			}
		} catch (Exception e) {
			Log.e(TAG, "eror waktu muat preferences sejarah", e); //$NON-NLS-1$
		}
	}

	public synchronized void save() {
		final int n = entries.size();
		final SharedPreferences.Editor editor = preferences.edit();

		editor.putInt(HISTORY_PREFIX + "n", n); //$NON-NLS-1$
		for (int i = 0; i < n; i++) {
			ClientHistoryEntry entry = entries.get(i);
			editor.putString(HISTORY_PREFIX + i,
			"v1" + FIELD_SEPARATOR_STRING
			+ entry.ari + FIELD_SEPARATOR_STRING
			+ entry.timestamp + FIELD_SEPARATOR_STRING
			+ (entry.savedInServer? "1": "0")
			);
		}

		if (Build.VERSION.SDK_INT >= 9) {
			editor.apply();
		} else {
			editor.commit();
		}
	}
	
	public synchronized void add(int ari) {
		// check: do we have this previously?
		for (int i = 0, len = entries.size(); i < len; i++) {
			final ClientHistoryEntry entry = entries.get(i);
			if (entry.ari == ari) {
				// YES. Move this to the front and update timestamp
				entries.remove(i);
				entry.timestamp = System.currentTimeMillis();
				entry.savedInServer = false;
				entries.add(0, entry);
				SyncUtil.requestSync("history_add");
				return;
			}
		}
		
		// NO. Add it to the front and remove if overflow
		ClientHistoryEntry entry = new ClientHistoryEntry();
		entry.ari = ari;
		entry.timestamp = System.currentTimeMillis();
		entry.savedInServer = false;
		entries.add(0, entry);

		if (entries.size() > MAX) {
			entries.remove(MAX);
		}

		SyncUtil.requestSync("history_add");
	}

	public synchronized int getSize() {
		return entries.size();
	}

	public synchronized int getAri(int i) {
		if (i >= entries.size()) {
			return 0;
		}
		return entries.get(i).ari;
	}

	public synchronized List<HistoryEntry> getEntriesToSend() {
		List<HistoryEntry> res = new ArrayList<HistoryEntry>();
		for (ClientHistoryEntry entry : entries) {
			if (!entry.savedInServer) {
				res.add(entry.toHistoryEntry());
			}
		}
		return res;
	}

	public synchronized void replaceAllWithServerData(final List<HistoryEntry> serverEntries) {
		entries.clear();
		for (HistoryEntry serverEntry : serverEntries) {
			final ClientHistoryEntry clientEntry = ClientHistoryEntry.fromHistoryEntry(true, serverEntry);
			entries.add(clientEntry);
		}
	}

	public long getTimestamp(final int position) {
		return entries.get(position).timestamp;
	}
}
