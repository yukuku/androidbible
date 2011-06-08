package yuku.alkitab.base.config;

import android.content.*;
import android.content.res.*;
import android.util.*;

import java.util.*;

import org.xmlpull.v1.*;

import yuku.alkitab.base.EdisiActivity.MEdisiPreset;
import yuku.alkitab.base.storage.*;

public class BuildConfig {
	public static final String TAG = BuildConfig.class.getSimpleName();

	public String internalPrefix;
	public String internalJudul;
	public boolean menuRenungan;
	public boolean menuGebug;
	public boolean menuEdisi;
	public boolean menuBantuan;
	public boolean menuDonasi;
	public List<MEdisiPreset> xpreset;

	private static BuildConfig lastConfig;
	private static String lastPackageName;
	
	private BuildConfig() {}
	
	public static BuildConfig get(Context context) {
		String packageName = context.getPackageName();
		if (packageName.equals(lastPackageName)) {
			return lastConfig;
		}
		
		int resId = context.getResources().getIdentifier("build_config_" + packageName.replace('.', '_'), "xml", packageName); //$NON-NLS-1$ //$NON-NLS-2$
		if (resId == 0) {
			return null;
		}
		
		BuildConfig res = null;
		try {
			res = loadConfig(context.getResources().getXml(resId));
			lastConfig = res;
			lastPackageName = packageName;
		} catch (Exception e) {
			Log.e(TAG, "error in loading build config", e); //$NON-NLS-1$
		}
		
		return res;
	}

	private static BuildConfig loadConfig(XmlResourceParser parser) throws Exception {
		BuildConfig res = new BuildConfig();
		
		List<MEdisiPreset> xpreset = new ArrayList<MEdisiPreset>();
		int urutanPreset = 10;

		while (true) {
			int next = parser.next();
			if (next == XmlPullParser.START_TAG && "menu".equals(parser.getName())) { //$NON-NLS-1$
				res.menuBantuan = parser.getAttributeBooleanValue(null, "bantuan", false); //$NON-NLS-1$
				res.menuDonasi = parser.getAttributeBooleanValue(null, "donasi", false); //$NON-NLS-1$
				res.menuEdisi = parser.getAttributeBooleanValue(null, "edisi", false); //$NON-NLS-1$
				res.menuGebug = parser.getAttributeBooleanValue(null, "gebug", false); //$NON-NLS-1$
				res.menuRenungan = parser.getAttributeBooleanValue(null, "renungan", false); //$NON-NLS-1$
			} else if (next == XmlPullParser.START_TAG && "internal".equals(parser.getName())) { //$NON-NLS-1$
				res.internalJudul = parser.getAttributeValue(null, "judul"); //$NON-NLS-1$
				res.internalPrefix = parser.getAttributeValue(null, "prefix"); //$NON-NLS-1$
			} else if (next == XmlPullParser.START_TAG && "preset".equals(parser.getName())) { //$NON-NLS-1$
				MEdisiPreset preset = new MEdisiPreset();
				preset.jenis = Db.Edisi.jenis_preset;
				preset.judul = parser.getAttributeValue(null, "judul"); //$NON-NLS-1$
				preset.namafile_preset = parser.getAttributeValue(null, "namafile_preset"); //$NON-NLS-1$
				preset.url = parser.getAttributeValue(null, "url"); //$NON-NLS-1$
				preset.urutan = ++urutanPreset;
				xpreset.add(preset);
			} else if (next == XmlPullParser.END_DOCUMENT) {
				break;
			}
		}
		
		res.xpreset = xpreset;
		
		return res;
	}
}
