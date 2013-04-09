package yuku.alkitab.base;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.InputStream;
import java.util.Locale;

import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.base.storage.InternalDbHelper;
import yuku.alkitab.base.storage.SongDb;
import yuku.alkitab.base.storage.SongDbHelper;
import yuku.alkitab.base.util.FontManager;


public class S {
	static final String TAG = S.class.getSimpleName();

	/**
	 * values applied from settings
	 */
	public static class applied {
		/** in dp */
		public static float fontSize2dp;
		
		public static Typeface fontFace;
		public static float lineSpacingMult;
		public static int fontBold;
		
		public static int fontColor;
		public static int fontRedColor;
		public static int backgroundColor;
		public static int verseNumberColor;
		
		/** 0.f to 1.f */
		public static float backgroundBrightness;
		
		// semua di bawah dalam px
		public static int indentParagraphFirst;
		public static int indentParagraphRest;
		public static int indentSpacing1;
		public static int indentSpacing2;
		public static int indentSpacing3;
		public static int indentSpacing4;
		public static int indentSpacingExtra;
		public static int paragraphSpacingBefore;
		public static int pericopeSpacingTop;
		public static int pericopeSpacingBottom;
	}
	
	//# 22nya harus siap di siapinKitab
	public static Version activeVersion;
	public static String activeVersionId;
	
	public static synchronized void prepareInternalVersion() {
		if (activeVersion == null) {
			activeVersion = Version.getInternalVersion();
		}
	}
	
	public static void calculateAppliedValuesBasedOnPreferences() {
		//# atur ukuran huruf isi berdasarkan pengaturan
		{
			applied.fontSize2dp = Preferences.getFloat(App.context.getString(R.string.pref_ukuranHuruf2_key), 17.f);
		}
		
		//# atur jenis huruf, termasuk boldnya
		{
			applied.fontFace = FontManager.typeface(Preferences.getString(App.context.getString(R.string.pref_jenisHuruf_key), null));
			applied.lineSpacingMult = Preferences.getFloat(App.context.getString(R.string.pref_lineSpacingMult_key), 1.f);
			applied.fontBold = Preferences.getBoolean(App.context.getString(R.string.pref_boldHuruf_key), false)? Typeface.BOLD: Typeface.NORMAL;
		}
		
		//# atur warna teks, latar, dan nomer ayat
		{
			applied.fontColor = Preferences.getInt(App.context.getString(R.string.pref_warnaHuruf_int_key), App.context.getResources().getInteger(R.integer.pref_warnaHuruf_int_default));
			applied.backgroundColor = Preferences.getInt(App.context.getString(R.string.pref_warnaLatar_int_key), App.context.getResources().getInteger(R.integer.pref_warnaLatar_int_default));
			applied.verseNumberColor = Preferences.getInt(App.context.getString(R.string.pref_warnaNomerAyat_int_key), App.context.getResources().getInteger(R.integer.pref_warnaNomerAyat_int_default));
			applied.fontRedColor = Preferences.getInt(App.context.getString(R.string.pref_redTextColor_key), App.context.getResources().getInteger(R.integer.pref_redTextColor_default));
			
			// calculation of backgroundColor brightness. Used somewhere else.
			{
				int c = applied.backgroundColor;
				applied.backgroundBrightness = (0.30f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c)) * 0.003921568627f;
			}
		}
		
		Resources res = App.context.getResources();
		
		float skalaBerdasarUkuranHuruf = applied.fontSize2dp / 17.f;
		applied.indentParagraphFirst = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indentParagraphFirst) + 0.5f);
		applied.indentParagraphRest = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indentParagraphRest) + 0.5f);
		applied.indentSpacing1 = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indent_1) + 0.5f);
		applied.indentSpacing2 = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indent_2) + 0.5f);
		applied.indentSpacing3 = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indent_3) + 0.5f);
		applied.indentSpacing4 = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indent_4) + 0.5f);
		applied.indentSpacingExtra = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indentExtra) + 0.5f);
		applied.paragraphSpacingBefore = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.paragraphSpacingBefore) + 0.5f);
		applied.pericopeSpacingTop = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.pericopeSpacingTop) + 0.5f);
		applied.pericopeSpacingBottom = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.pericopeSpacingBottom) + 0.5f);
	}
	
	/**
	 * @param handler Jangan null kalo mau dicek ulang 200ms kemudian. Harus null kalo jangan ulang lagi. 20110620 Uda ga dipake lagi.
	 */
	public static void applyLanguagePreference(final Handler handler, final int cobaLagi) {
		String bahasa = Preferences.getString(App.context.getString(R.string.pref_bahasa_key), App.context.getString(R.string.pref_bahasa_default));

		Locale locale;
		if ("DEFAULT".equals(bahasa)) { //$NON-NLS-1$
			locale = Locale.getDefault();
		} else {
			locale = new Locale(bahasa);
		}
		
		App.updateConfigurationWithLocale(App.context.getResources().getConfiguration(), locale);
	}

	private static TObjectIntHashMap<String> cache_rawResId = new TObjectIntHashMap<String>(32);
	
	public static InputStream openRaw(String name) {
		Resources resources = App.context.getResources();
		
		int resId = cache_rawResId.get(name);
		if (resId != 0) {
			return resources.openRawResource(resId);
		}
		
		resId = resources.getIdentifier(name, "raw", App.context.getPackageName()); //$NON-NLS-1$
		if (resId == 0) {
			return null;
		}
		
		cache_rawResId.put(name, resId);
		return resources.openRawResource(resId);
	}
	
	private static InternalDb db;
	public static synchronized InternalDb getDb() {
		if (db == null) {
			db = new InternalDb(new InternalDbHelper(App.context));
		}
		
		return db;
	}
	
	private static SongDb songDb;
	public static synchronized SongDb getSongDb() {
		if (songDb == null) {
			songDb = new SongDb(new SongDbHelper());
		}
		
		return songDb;
	}
}
