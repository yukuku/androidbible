package yuku.alkitab.base.model;

import android.util.Log;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.storage.YesReaderFactory;
import yuku.alkitab.io.BibleReader;
import yuku.alkitab.model.Version;

import java.io.File;

/**
 * Version that is defined in the database.
 * If the version is downloaded from a definition in the preset list, the {@link #preset_name} will be non-null.
 */
public class MVersionDb extends MVersion {
	public String filename;
	public String preset_name;
	public boolean cache_active; // so we don't need to keep reading/writing from/to db
	/**
	 * The last-known update time of the version, so we can notify the user if their downloaded versions has update.
	 * If this is 0, it means the version does not support update notification, because the version is converted from legacy version of this app,
	 * or added from pdb/yes files manually.
	 */
	public int modifyTime;

	@Override
	public String getVersionId() {
		if (preset_name != null) {
			return "preset/" + preset_name;
		}
		return "file/" + filename;
	}

	@Override
	public Version getVersion() {
		if (hasDataFile()) {
			final BibleReader reader = YesReaderFactory.createYesReader(filename);
			if (reader == null) {
				Log.e(VersionsActivity.TAG, "YesReaderFactory failed to open the yes file");
				return null;
			}
			return new VersionImpl(reader);
		} else {
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
