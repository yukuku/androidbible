package yuku.alkitab.base.model;

import yuku.alkitab.model.Version;

/**
 * Internal version, only one
 */
public class MVersionInternal extends MVersion {
	public static String getVersionInternalId() {
		return "internal";
	}

	@Override public String getVersionId() {
		return getVersionInternalId();
	}

	@Override
	public Version getVersion() {
		return VersionImpl.getInternalVersion();
	}

	@Override
	public boolean getActive() {
		return true; // always active
	}

	@Override public boolean hasDataFile() {
		return true; // always has
	}
}
