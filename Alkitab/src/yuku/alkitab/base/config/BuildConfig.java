package yuku.alkitab.base.config;

import java.util.*;

import org.xmlpull.v1.XmlPullParser;

import yuku.alkitab.base.EdisiActivity.MEdisiPreset;
import yuku.alkitab.base.storage.Db;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;

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
		
		int resId = context.getResources().getIdentifier("build_config_" + packageName.replace('.', '_'), "xml", packageName);
		if (resId == 0) {
			return null;
		}
		
		BuildConfig res = null;
		try {
			res = loadConfig(context.getResources().getXml(resId));
			lastConfig = res;
			lastPackageName = packageName;
		} catch (Exception e) {
			Log.e(TAG, "error in loading build config", e);
		}
		
		return res;
	}

	private static BuildConfig loadConfig(XmlResourceParser parser) throws Exception {
		BuildConfig res = new BuildConfig();
		
		List<MEdisiPreset> xpreset = new ArrayList<MEdisiPreset>();
		int urutanPreset = 10;

		while (true) {
			int next = parser.next();
			if (next == XmlPullParser.START_TAG && "menu".equals(parser.getName())) {
				res.menuBantuan = parser.getAttributeBooleanValue(null, "bantuan", false);
				res.menuDonasi = parser.getAttributeBooleanValue(null, "donasi", false);
				res.menuEdisi = parser.getAttributeBooleanValue(null, "edisi", false);
				res.menuGebug = parser.getAttributeBooleanValue(null, "gebug", false);
				res.menuRenungan = parser.getAttributeBooleanValue(null, "renungan", false);
			} else if (next == XmlPullParser.START_TAG && "internal".equals(parser.getName())) {
				res.internalJudul = parser.getAttributeValue(null, "judul");
				res.internalPrefix = parser.getAttributeValue(null, "prefix");
			} else if (next == XmlPullParser.START_TAG && "preset".equals(parser.getName())) {
				MEdisiPreset preset = new MEdisiPreset();
				preset.aktif = false;
				preset.jenis = Db.Edisi.jenis_preset;
				preset.judul = parser.getAttributeValue(null, "judul");
				preset.namafile_preset = parser.getAttributeValue(null, "namafile_preset");
				preset.url = parser.getAttributeValue(null, "url");
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
