package yuku.alkitab.base.model;

import androidx.annotation.Nullable;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.model.Version;

/**
 * Internal version, only one
 */
public class MVersionInternal extends MVersion {
	public static final int DEFAULT_ORDERING = 1;

	public static String getVersionInternalId() {
		return "internal";
	}

	@Override public String getVersionId() {
		return getVersionInternalId();
	}

	/**
	 * The bundled internal version is a preset build (e.g. TB on {@code yuku_alkitab},
	 * KJV on {@code yuku_quick_bible}); each product flavor declares which via
	 * {@code BuildConfig.INTERNAL_VERSION_PRESET_NAME}. An empty value means the
	 * flavor's internal version has no preset identity, reported as null like any
	 * other preset-less version.
	 */
	@Nullable @Override public String getPresetName() {
		final String presetName = BuildConfig.INTERNAL_VERSION_PRESET_NAME;
		return presetName.isEmpty() ? null : presetName;
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
