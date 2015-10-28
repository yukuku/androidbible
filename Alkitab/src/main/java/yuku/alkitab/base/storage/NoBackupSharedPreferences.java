package yuku.alkitab.base.storage;

import android.support.v4.content.ContextCompat;
import android.support.v4.util.AtomicFile;
import yuku.alkitab.base.App;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class NoBackupSharedPreferences {
	static HashMap<String, NoBackupSharedPreferences> instances = new HashMap<>();

	final File file;

	static class Map extends LinkedHashMap<String, Object> {}

	Map map;

	public static NoBackupSharedPreferences get() {
		return get("default.xml");
	}

	public static synchronized NoBackupSharedPreferences get(final String filename) {
		NoBackupSharedPreferences res = instances.get(filename);
		if (res == null) {
			res = new NoBackupSharedPreferences(filename);
			instances.put(filename, res);
		}
		return res;
	}

	private NoBackupSharedPreferences(final String filename) {
		file = new File(new ContextCompat().getNoBackupFilesDir(App.context), filename);
		if (file.exists()) {
			final AtomicFile atom = new AtomicFile(file);
			try {
				final FileInputStream fis = atom.openRead();
				map = App.getDefaultGson().fromJson(new InputStreamReader(fis, Charset.forName("utf-8")), Map.class);
				fis.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			map = new Map();
		}
	}

	private void save() {
		final AtomicFile atom = new AtomicFile(file);
		try {
			final FileOutputStream fos = atom.startWrite();
			final OutputStreamWriter w = new OutputStreamWriter(fos, Charset.forName("utf-8"));
			App.getDefaultGson().toJson(map, w);
			w.flush();
			atom.finishWrite(fos);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void setString(final String key, final String value) {
		map.put(key, value);
		save();
	}

	public String getString(final String key) {
		final Object value = map.get(key);
		if (value == null) {
			return null;
		}

		if (!(value instanceof String)) {
			return value.toString();
		}

		return (String) value;
	}
}
