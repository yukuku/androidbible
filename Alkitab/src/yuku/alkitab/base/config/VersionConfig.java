package yuku.alkitab.base.config;

import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParser;
import yuku.afw.App;
import yuku.alkitab.base.ac.VersionsActivity.MVersionPreset;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.debug.R;

import java.util.ArrayList;
import java.util.List;

public class VersionConfig {
	public static final String TAG = VersionConfig.class.getSimpleName();

	public String internalPrefix;
	public String internalShortName;
	public String internalLongName;
	public List<MVersionPreset> presets;

	private static VersionConfig lastVersionConfig;

	private VersionConfig() {}
	
	public static VersionConfig get() {
		if (lastVersionConfig != null) {
			return lastVersionConfig;
		}
		
		try {
			VersionConfig res = loadConfig(App.context.getResources().getXml(R.xml.version_config));
			lastVersionConfig = res;
			return res;
		} catch (Exception e) {
			throw new RuntimeException("error in loading version config", e);
		}
	}

	private static VersionConfig loadConfig(XmlResourceParser parser) throws Exception {
		final VersionConfig res = new VersionConfig();
		
		final List<MVersionPreset> presets = new ArrayList<>();
		int presetOrdering = 10;

		while (true) {
			int next = parser.next();
			final String tagName = parser.getName();
			if (next == XmlPullParser.START_TAG && "internal".equals(tagName)) {
				res.internalShortName = parser.getAttributeValue(null, "shortName"); 
				res.internalLongName = parser.getAttributeValue(null, "longName"); 
				res.internalPrefix = parser.getAttributeValue(null, "prefix"); 
			} else if (next == XmlPullParser.START_TAG && "preset".equals(tagName)) {
				MVersionPreset preset = new MVersionPreset();
				preset.type = Db.Version.kind_preset;
				preset.shortName = parser.getAttributeValue(null, "shortName"); 
				preset.longName = parser.getAttributeValue(null, "longName"); 
				preset.presetFilename = parser.getAttributeValue(null, "filename_preset"); 
				preset.url = parser.getAttributeValue(null, "url"); 
				preset.ordering = ++presetOrdering;
				preset.locale = parser.getAttributeValue(null, "locale"); 
				presets.add(preset);
			} else if (next == XmlPullParser.END_DOCUMENT) {
				break;
			}
		}
		
		res.presets = presets;

		return res;
	}
}
