package yuku.alkitab.base.config;

import android.support.v4.util.AtomicFile;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.model.MVersionPreset;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AppLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VersionConfig {
	public static final String TAG = VersionConfig.class.getSimpleName();

	public List<MVersionPreset> presets;
	public Map<String, String> locale_display;

	private static VersionConfig instance;

	private VersionConfig() {
	}

	static class PresetJson {
		public String locale;
		public String shortName;
		public String longName;
		public String description;
		public String preset_name;
		public int modifyTime;
	}

	static class VersionConfigJson {
		public List<PresetJson> presets;
		public String download_url_format;
		public Map<String, String> locale_display;
	}

	public static VersionConfig get() {
		if (instance != null) {
			return instance;
		}

		final VersionConfig latest = loadLatest();
		instance = latest;
		return latest;
	}

	private static VersionConfig loadLatest() {
		final File updatedFile = getUpdatedFile();

		if (updatedFile.exists() && updatedFile.canRead() && updatedFile.length() > 0) {
			try {
				final AtomicFile file = new AtomicFile(updatedFile);
				final String json = new String(file.readFully(), "utf-8");
				final VersionConfigJson obj = App.getDefaultGson().fromJson(json, VersionConfigJson.class);
				return convertConfig(obj);
			} catch (Exception e) {
				// failed to load updated file, fallback to embedded
				Preferences.setInt(Prefkey.version_config_current_modify_time, 0);
				Preferences.setInt(Prefkey.version_config_last_update_check, 0);
				// fallthrough
			}
		}

		try {
			final InputStreamReader reader = new InputStreamReader(App.context.getAssets().open("version_config.json"), "utf-8");
			final VersionConfigJson obj = App.getDefaultGson().fromJson(reader, VersionConfigJson.class);
			reader.close();

			return convertConfig(obj);
		} catch (IOException e) {
			throw new RuntimeException("error in loading embedded version config", e);
		}
	}

	private static File getUpdatedFile() {
		return new File(App.context.getFilesDir(), "version_config.json");
	}

	/**
	 * Modify time for the version available for download.
	 *
	 * @return 0 if preset_name not known, or no updates supported.
	 */
	public int getModifyTime(final String preset_name) {
		if (preset_name == null) return 0;

		for (final MVersionPreset preset : presets) {
			if (preset_name.equals(preset.preset_name)) {
				return preset.modifyTime;
			}
		}

		return 0;
	}

	public MVersionPreset getPreset(final String preset_name) {
		if (preset_name == null) return null;

		for (final MVersionPreset preset : presets) {
			if (preset_name.equals(preset.preset_name)) {
				return preset;
			}
		}

		return null;
	}

	/**
	 * Does not do anything persistent.
	 */
	private static VersionConfig convertConfig(VersionConfigJson root) {
		final VersionConfig res = new VersionConfig();

		final List<MVersionPreset> presets = new ArrayList<>();
		int presetOrdering = 10;

		for (PresetJson presetJson : root.presets) {
			final MVersionPreset preset = new MVersionPreset();
			preset.locale = presetJson.locale;
			preset.shortName = presetJson.shortName;
			preset.longName = presetJson.longName;
			preset.description = presetJson.description;
			preset.preset_name = presetJson.preset_name;
			preset.modifyTime = presetJson.modifyTime;
			preset.download_url = root.download_url_format.replace("$PRESET_NAME", presetJson.preset_name);
			preset.ordering = ++presetOrdering;
			presets.add(preset);
		}

		res.locale_display = root.locale_display;
		res.presets = presets;

		return res;
	}

	/** Checks if the given json is a valid version config file */
	public static boolean isValid(final String json) {
		try {
			convertConfig(App.getDefaultGson().fromJson(json, VersionConfigJson.class));
			return true;
		} catch (Exception e) {
			AppLog.d(TAG, "@@isValid not valid json file", e);
			return false;
		}
	}

	/**
	 * Must be assumed that the given json is valid.
	 * Pass the json to the {@link #isValid(String)} first.
	 *
	 * @return true when success
	 */
	public static boolean useLatest(final String json, final int modifyTime) {
		try {
			final AtomicFile file = new AtomicFile(getUpdatedFile());
			final FileOutputStream fos = file.startWrite();
			fos.write(json.getBytes("utf-8"));
			file.finishWrite(fos);
		} catch (IOException e) {
			AppLog.d(TAG, "Failed to write to update file", e);
			return false;
		}

		Preferences.setInt(Prefkey.version_config_current_modify_time, modifyTime);
		instance = loadLatest();
		return true;
	}
}
