package yuku.alkitab.base.config;

import android.content.res.XmlResourceParser;
import android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import yuku.afw.App;
import yuku.alkitab.base.ac.VersionsActivity.MVersionPreset;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.debug.R;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
	public static final String TAG = AppConfig.class.getSimpleName();

	public String internalPrefix;
	public String internalShortName;
	public String internalLongName;
	public boolean menuDevotion;
	public boolean menuHelp;
	public boolean menuDonation;
	public boolean menuSongs;
	public List<MVersionPreset> presets;
	public String shareUrlFormat;

	private static AppConfig lastAppConfig;

	private AppConfig() {}
	
	public static AppConfig get() {
		if (lastAppConfig != null) {
			return lastAppConfig;
		}
		
		AppConfig res = null;
		try {
			res = loadConfig(App.context.getResources().getXml(R.xml.app_config));
			lastAppConfig = res;
		} catch (Exception e) {
			Log.e(TAG, "error in loading app config", e); //$NON-NLS-1$
		}
		
		return res;
	}

	private static AppConfig loadConfig(XmlResourceParser parser) throws Exception {
		AppConfig res = new AppConfig();
		
		List<MVersionPreset> presets = new ArrayList<MVersionPreset>();
		int presetOrdering = 10;

		while (true) {
			int next = parser.next();
			if (next == XmlPullParser.START_TAG && "menu".equals(parser.getName())) { //$NON-NLS-1$
				res.menuHelp = parser.getAttributeBooleanValue(null, "help", false); //$NON-NLS-1$
				res.menuDonation = parser.getAttributeBooleanValue(null, "donation", false); //$NON-NLS-1$
				res.menuDevotion = parser.getAttributeBooleanValue(null, "devotion", false); //$NON-NLS-1$
				res.menuSongs = parser.getAttributeBooleanValue(null, "songs", false); //$NON-NLS-1$
			} else if (next == XmlPullParser.START_TAG && "internal".equals(parser.getName())) { //$NON-NLS-1$
				res.internalShortName = parser.getAttributeValue(null, "shortName"); //$NON-NLS-1$
				res.internalLongName = parser.getAttributeValue(null, "longName"); //$NON-NLS-1$
				res.internalPrefix = parser.getAttributeValue(null, "prefix"); //$NON-NLS-1$
			} else if (next == XmlPullParser.START_TAG && "preset".equals(parser.getName())) { //$NON-NLS-1$
				MVersionPreset preset = new MVersionPreset();
				preset.type = Db.Version.kind_preset;
				preset.shortName = parser.getAttributeValue(null, "shortName"); //$NON-NLS-1$
				preset.longName = parser.getAttributeValue(null, "longName"); //$NON-NLS-1$
				preset.presetFilename = parser.getAttributeValue(null, "filename_preset"); //$NON-NLS-1$
				preset.url = parser.getAttributeValue(null, "url"); //$NON-NLS-1$
				preset.ordering = ++presetOrdering;
				preset.locale = parser.getAttributeValue(null, "locale"); //$NON-NLS-1$
				presets.add(preset);
			} else if (next == XmlPullParser.START_TAG && "url".equals(parser.getName())) { //$NON-NLS-1$
				res.shareUrlFormat = parser.getAttributeValue(null, "format"); //$NON-NLS-1$
			} else if (next == XmlPullParser.END_DOCUMENT) {
				break;
			}
		}
		
		res.presets = presets;

		return res;
	}
}
