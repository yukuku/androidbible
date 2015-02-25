package yuku.alkitab.base.model;

import android.util.Log;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.storage.YesReaderFactory;
import yuku.alkitab.io.BibleReader;
import yuku.alkitab.model.Version;

import java.io.File;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version that is defined in the database.
 * If the version is downloaded from a definition in the preset list, the {@link #preset_name} will be non-null.
 */
public class MVersionDb extends MVersion {
	public static final int DEFAULT_ORDERING_START = 100;

	public String filename;
	public String preset_name;
	public boolean cache_active; // so we don't need to keep reading/writing from/to db
	/**
	 * The last-known update time of the version, so we can notify the user if their downloaded versions has update.
	 * If this is 0, it means the version does not support update notification, because the version is converted from legacy version of this app,
	 * or added from pdb/yes files manually.
	 */
	public int modifyTime;

	/**
	 * VersionImpl instance cache, so we do not reinstantiate VersionImpl objects every time
	 * we access versions. Especially when doing a version compare.
	 */
	static ConcurrentHashMap<String /* filename */, SoftReference<VersionImpl>> impl_cache = new ConcurrentHashMap<>();

	/**
	 * The version id for MVersionDb can be "preset/" followed by preset_name,
	 * or "file/" followed by absolute path name of the yes file.
	 *
	 * Therefore, the id starting with "preset/" does not always indicate MVersionPreset,
	 * since it probably indicate MVersionDb as well.
	 */
	@Override
	public String getVersionId() {
		if (preset_name != null) {
			return "preset/" + preset_name;
		}
		return "file/" + filename;
	}

	/**
	 * Return the preset_name of a version id, if possible. Null otherwise.
	 */
	public static String presetNameFromVersionId(String versionId) {
		if (versionId != null && versionId.startsWith("preset/")) {
			return versionId.substring(7);
		} else {
			return null;
		}
	}

	@Override
	public Version getVersion() {
		if (hasDataFile()) {
			// check cache first
			final SoftReference<VersionImpl> ref = impl_cache.get(filename);
			if (ref != null) {
				final VersionImpl res = ref.get();
				if (res != null) {
					return res;
				}
			}

			final BibleReader reader = YesReaderFactory.createYesReader(filename);
			if (reader == null) {
				Log.e(VersionsActivity.TAG, "YesReaderFactory failed to open the yes file");
				return null;
			}

			final VersionImpl res = new VersionImpl(reader);

			// put to cache
			impl_cache.put(filename, new SoftReference<>(res));

			return res;
		} else {
			// clear from cache if any
			impl_cache.remove(filename);

			return null;
		}
	}

	public void setActive(boolean active) {
		this.cache_active = active;
		S.getDb().setVersionActive(this, active);
	}

	@Override
	public boolean getActive() {
		return this.cache_active;
	}

	@Override public boolean hasDataFile() {
		final File f = new File(filename);
		return f.exists() && f.canRead();
	}
}
