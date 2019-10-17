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
	/** return version so that it can be read. Null when not possible */
	@Nullable public abstract Version getVersion();
	public abstract boolean getActive();
	public abstract boolean hasDataFile();
}
