package yuku.alkitab.base.config;

import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParser;
import yuku.afw.App;
import yuku.alkitab.debug.R;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
	public boolean menuDevotion;
	public boolean menuSongs;
	public boolean menuDictionary;
	public boolean menuGuide;
	public boolean menuCommentary;

	public String internalPrefix;
	public String internalLocale;
	public String internalShortName;
	public String internalLongName;
	public String internalPresetName;

	public List<String> devotionNames = new ArrayList<>();

	private static AppConfig lastAppConfig;

	private AppConfig() {
	}

	public static AppConfig get() {
		if (lastAppConfig != null) {
			return lastAppConfig;
		}

		try {
			AppConfig res = loadConfig(App.context.getResources().getXml(R.xml.app_config));
			lastAppConfig = res;
			return res;
		} catch (Exception e) {
			throw new RuntimeException("error in loading app config", e);
		}
	}

	private static AppConfig loadConfig(XmlResourceParser parser) throws Exception {
		final AppConfig res = new AppConfig();

		while (true) {
			final int next = parser.next();
			if (next == XmlPullParser.START_TAG) {
				final String tagName = parser.getName();
				switch (tagName) {
					case "internal":
						res.internalLocale = parser.getAttributeValue(null, "locale");
						res.internalShortName = parser.getAttributeValue(null, "shortName");
						res.internalLongName = parser.getAttributeValue(null, "longName");
						res.internalPrefix = parser.getAttributeValue(null, "prefix");
						res.internalPresetName = parser.getAttributeValue(null, "preset_name");
						break;
					case "menu":
						res.menuDevotion = parser.getAttributeBooleanValue(null, "devotion", false);
						res.menuSongs = parser.getAttributeBooleanValue(null, "songs", false);
						res.menuDictionary = parser.getAttributeBooleanValue(null, "dictionary", false);
						res.menuGuide = parser.getAttributeBooleanValue(null, "guide", false);
						res.menuCommentary = parser.getAttributeBooleanValue(null, "commentary", false);
						break;
					case "devotion":
						res.devotionNames.add(parser.getAttributeValue(null, "name"));
						break;
				}
			} else if (next == XmlPullParser.END_DOCUMENT) {
				break;
			}
		}

		return res;
	}
}
