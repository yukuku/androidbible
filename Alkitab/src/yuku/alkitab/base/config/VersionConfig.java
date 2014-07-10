package yuku.alkitab.base.config;

import com.google.gson.Gson;
import yuku.afw.App;
import yuku.alkitab.base.ac.VersionsActivity.MVersionPreset;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class VersionConfig {
	public static final String TAG = VersionConfig.class.getSimpleName();

	public List<MVersionPreset> presets;

	private static VersionConfig lastVersionConfig;

	private VersionConfig() {}

	static class PresetJson {
		public String locale;
		public String shortName;
		public String longName;
		public String description;
		public String filename_preset;
		public int modifyTime;
	}

	static class VersionConfigJson {
		public List<PresetJson> presets;
		public String download_url_format;
	}

	public static VersionConfig get() {
		if (lastVersionConfig != null) {
			return lastVersionConfig;
		}

		try {
			final InputStreamReader reader = new InputStreamReader(App.context.getAssets().open("version_config.json"), "utf-8");
			final VersionConfigJson versionConfigJson = new Gson().fromJson(reader, VersionConfigJson.class);
			reader.close();

			VersionConfig res = loadConfig(versionConfigJson);
			lastVersionConfig = res;
			return res;
		} catch (Exception e) {
			throw new RuntimeException("error in loading version config", e);
		}
	}

	private static VersionConfig loadConfig(VersionConfigJson root) throws Exception {
		final VersionConfig res = new VersionConfig();
		
		final List<MVersionPreset> presets = new ArrayList<>();
		int presetOrdering = 10;

		for (PresetJson presetJson : root.presets) {
			final MVersionPreset preset = new MVersionPreset();
			preset.locale = presetJson.locale;
			preset.shortName = presetJson.shortName;
			preset.longName = presetJson.longName;
			preset.description = presetJson.description;
			preset.presetFilename = presetJson.filename_preset;
			preset.download_url = root.download_url_format.replace("$FILENAME_PRESET", presetJson.filename_preset);
			preset.ordering = ++presetOrdering;
			presets.add(preset);
		}
		
		res.presets = presets;

		return res;
	}
}
