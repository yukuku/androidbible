package yuku.alkitab.base.config;

import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParser;
import yuku.afw.App;
import yuku.alkitab.debug.R;

public class AppConfig {
	public static final String TAG = AppConfig.class.getSimpleName();

	public boolean menuDevotion;
	public boolean menuHelp;
	public boolean menuDonation;
	public boolean menuSongs;
	public String shareUrlFormat;

	private static AppConfig lastAppConfig;

	private AppConfig() {}
	
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
			int next = parser.next();
			if (next == XmlPullParser.START_TAG && "menu".equals(parser.getName())) { //$NON-NLS-1$
				res.menuHelp = parser.getAttributeBooleanValue(null, "help", false); //$NON-NLS-1$
				res.menuDonation = parser.getAttributeBooleanValue(null, "donation", false); //$NON-NLS-1$
				res.menuDevotion = parser.getAttributeBooleanValue(null, "devotion", false); //$NON-NLS-1$
				res.menuSongs = parser.getAttributeBooleanValue(null, "songs", false); //$NON-NLS-1$
			} else if (next == XmlPullParser.START_TAG && "url".equals(parser.getName())) { //$NON-NLS-1$
				res.shareUrlFormat = parser.getAttributeValue(null, "format"); //$NON-NLS-1$
			} else if (next == XmlPullParser.END_DOCUMENT) {
				break;
			}
		}
		
		return res;
	}
}
