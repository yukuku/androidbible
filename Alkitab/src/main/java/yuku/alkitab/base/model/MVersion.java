package yuku.alkitab.base.model;

import androidx.annotation.Nullable;
import yuku.alkitab.model.Version;

// models
public abstract class MVersion {
	public String locale;
	public String shortName;
	public String longName;
	public String description;
	public int ordering;

	/**
	 * This is populated when preset list is loaded (Not persisted in DB)
	 */
	public transient int group_order;

	/** unique id for comparison purposes */
	public abstract String getVersionId();

	/**
	 * Name of the preset this version was built from (e.g. {@code "in-tb"}), or null
	 * when the version has no preset identity (e.g. a version side-loaded from a
	 * yes/pdb file). This is what {@code /versions/get_yes?preset_name=…} downloads
	 * against, and what per-version backend queries such as {@code /audio/sets/<preset>}
	 * key on.
	 */
	@Nullable public String getPresetName() {
		return null;
	}

	/** return version so that it can be read. Null when not possible */
	@Nullable public abstract Version getVersion();
	public abstract boolean getActive();
	public abstract boolean hasDataFile();
}
