package yuku.alkitab.base.config;

import com.google.gson.Gson;
import yuku.afw.App;
import yuku.alkitab.base.model.MVersionPreset;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VersionConfig {
	public static final String TAG = VersionConfig.class.getSimpleName();

	public List<MVersionPreset> presets;
	public Map<String, String> locale_display;

	private static VersionConfig lastVersionConfig;

	private VersionConfig() {}

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
		} catch (IOException e) {
			throw new RuntimeException("error in loading version config", e);
		}
	}

	private static VersionConfig loadConfig(VersionConfigJson root) {
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
}
