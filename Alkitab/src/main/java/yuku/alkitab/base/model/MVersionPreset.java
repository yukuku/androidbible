package yuku.alkitab.base.model;

import yuku.alkitab.base.util.AddonManager;
import yuku.alkitab.model.Version;

/**
 * Version that is defined in the version_config.json, but not activated by the user.
 */
public class MVersionPreset extends MVersion {
	public String download_url;
	public String preset_name;
	public int modifyTime;
	public boolean hidden;

	@Override public boolean getActive() {
		return false; // preset can't be active, because there is no data file activated by the user.
	}

	@Override
	public String getVersionId() {
		return "preset/" + preset_name;
	}

	@Override
	public Version getVersion() {
		throw new RuntimeException("THIS SHOULD NOT HAPPEN: preset should not have any actual data file to read from.");
	}

	@Override public boolean hasDataFile() {
		return AddonManager.hasVersion(preset_name + ".yes");
	}
}
