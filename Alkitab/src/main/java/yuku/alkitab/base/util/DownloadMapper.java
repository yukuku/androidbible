package yuku.alkitab.base.util;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;

import java.util.LinkedHashMap;
import java.util.Map;

public enum DownloadMapper implements SharedPreferences.OnSharedPreferenceChangeListener {
	instance;

	private static final String PREFERENCES_KEY = "download_mapper";

	static class Row {
		public String key;
		public long id;
		public Map<String, String> attrs;
	}

	static class ValueJson {
		public Row[] rows;
	}

	final Map<String, Row> currentByKey = new LinkedHashMap<>();
	final Map<Long, Row> currentById = new LinkedHashMap<>();
	final DownloadManager dm;

	DownloadMapper() {
		Preferences.registerObserver(this);

		dm = (DownloadManager) App.context.getSystemService(Context.DOWNLOAD_SERVICE);
		load();
	}

	void load() {
		final String json = Preferences.getString(PREFERENCES_KEY);
		if (json != null) {
			final ValueJson value = App.getDefaultGson().fromJson(json, ValueJson.class);
			if (value != null && value.rows != null) {
				currentByKey.clear();
				currentById.clear();
				for (final Row row : value.rows) {
					currentByKey.put(row.key, row);
					currentById.put(row.id, row);
				}
			}
		}
	}

	void save() {
		final ValueJson value = new ValueJson();
		value.rows = new Row[currentByKey.size()];
		int c = 0;
		for (final Row row : currentByKey.values()) {
			value.rows[c++] = row;
		}
		final String json = App.getDefaultGson().toJson(value);
		Preferences.setString(PREFERENCES_KEY, json);
	}

	public int getStatus(final String downloadKey) {
		final Row row = currentByKey.get(downloadKey);
		return getStatus(row);
	}

	public int getStatus(final long id) {
		final Row row = currentById.get(id);
		return getStatus(row);
	}

	private int getStatus(final Row row) {
		if (row == null) {
			return 0;
		} else {
			final Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(row.id));
			try {
				if (cursor.moveToNext()) {
					return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
				} else {
					// stale data found. Remove immediately
					currentByKey.remove(row.key);
					currentById.remove(row.id);
					save();
					return 0;
				}
			} finally {
				cursor.close();
			}
		}
	}

	// Must be called only after verifying that this id exists.
	public Map<String, String> getAttrs(final long id) {
		final Row row = currentById.get(id);
		return row.attrs;
	}

	public void enqueue(final String downloadKey, final DownloadManager.Request req, final Map<String, String> attrs) {
		final long id = dm.enqueue(req);
		final Row row = new Row();
		row.id = id;
		row.key = downloadKey;
		row.attrs = new LinkedHashMap<>(attrs);
		currentByKey.put(downloadKey, row);
		currentById.put(row.id, row);
		save();
	}

	public void remove(final long id) {
		dm.remove(id);

		final Row row = currentById.get(id);
		if (row != null) {
			currentByKey.remove(row.key);
			currentById.remove(row.id);
			save();
		}
	}

	@Override
	public void onSharedPreferenceChanged(final SharedPreferences pref, final String key) {
		if (PREFERENCES_KEY.equals(key)) {
			load();
		}
	}
}
