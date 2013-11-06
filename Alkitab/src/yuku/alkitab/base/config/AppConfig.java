package yuku.alkitab.base.config;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import yuku.afw.App;
import yuku.alkitab.base.ac.VersionsActivity.MVersionPreset;
import yuku.alkitab.base.model.ReadingPlan;
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
	public boolean menuGebug;
	public boolean menuVersions;
	public boolean menuHelp;
	public boolean menuDonation;
	public boolean menuSongs;
	public List<MVersionPreset> presets;
	public String url_prefix;
	public String url_format;
	public String[] url_standardBookNames;
	public List<ReadingPlan.ReadingPlanInfo> readingPlanInfos;
	
	private static AppConfig lastAppConfig;
	private static String lastPackageName;
	
	private AppConfig() {}
	
	public static AppConfig get() {
		String packageName = App.context.getPackageName();
		if (packageName.equals(lastPackageName)) {
			return lastAppConfig;
		}
		
		AppConfig res = null;
		try {
			res = loadConfig(App.context, App.context.getResources().getXml(R.xml.app_config));
			lastAppConfig = res;
			lastPackageName = packageName;
		} catch (Exception e) {
			Log.e(TAG, "error in loading build config", e); //$NON-NLS-1$
		}
		
		return res;
	}

	private static AppConfig loadConfig(Context context, XmlResourceParser parser) throws Exception {
		AppConfig res = new AppConfig();
		
		List<MVersionPreset> presets = new ArrayList<MVersionPreset>();
		List<ReadingPlan.ReadingPlanInfo> infos = new ArrayList<ReadingPlan.ReadingPlanInfo>();
		int presetOrdering = 10;

		while (true) {
			int next = parser.next();
			if (next == XmlPullParser.START_TAG && "menu".equals(parser.getName())) { //$NON-NLS-1$
				res.menuHelp = parser.getAttributeBooleanValue(null, "bantuan", false); //$NON-NLS-1$
				res.menuDonation = parser.getAttributeBooleanValue(null, "donasi", false); //$NON-NLS-1$
				res.menuVersions = parser.getAttributeBooleanValue(null, "edisi", false); //$NON-NLS-1$
				res.menuGebug = parser.getAttributeBooleanValue(null, "gebug", false); //$NON-NLS-1$
				res.menuDevotion = parser.getAttributeBooleanValue(null, "renungan", false); //$NON-NLS-1$
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
				// TODO support more url's if needed. now only one.
				res.url_prefix = parser.getAttributeValue(null, "prefix"); //$NON-NLS-1$
				res.url_format = parser.getAttributeValue(null, "format"); //$NON-NLS-1$
				res.url_standardBookNames = context.getResources().getStringArray(context.getResources().getIdentifier("nama_kitab_standar_" + parser.getAttributeValue(null, "lang"), "array", context.getPackageName())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				if (res.url_prefix == null || res.url_format == null || res.url_standardBookNames == null) {
					throw new RuntimeException("wrong share url config!"); //$NON-NLS-1$
				}
			}else if (next == XmlPullParser.START_TAG && "rp_preset".equals(parser.getName())) {
				ReadingPlan.ReadingPlanInfo info = new ReadingPlan.ReadingPlanInfo();
				info.title = parser.getAttributeValue(null, "title");
				info.description = parser.getAttributeValue(null, "description");
				info.filename = parser.getAttributeValue(null, "filename_rp_preset");
				info.url = parser.getAttributeValue(null, "url");
				infos.add(info);
			} else if (next == XmlPullParser.END_DOCUMENT) {
				break;
			}
		}
		
		res.presets = presets;
		res.readingPlanInfos = infos;
		
		return res;
	}
}
