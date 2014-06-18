package yuku.alkitab.base;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Pair;
import gnu.trove.map.hash.TObjectIntHashMap;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.config.VersionConfig;
import yuku.alkitab.base.model.VersionImpl;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.base.storage.InternalDbHelper;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


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
			activeVersion = VersionImpl.getInternalVersion();
		}
	}
	
	public static void calculateAppliedValuesBasedOnPreferences() {
		//# configure font size
		{
			applied.fontSize2dp = Preferences.getFloat(Prefkey.ukuranHuruf2, (float) App.context.getResources().getInteger(R.integer.pref_ukuranHuruf2_default));
		}
		
		//# configure fonts
		{
			applied.fontFace = FontManager.typeface(Preferences.getString(Prefkey.jenisHuruf, null));
			applied.lineSpacingMult = Preferences.getFloat(Prefkey.lineSpacingMult, 1.15f);
			applied.fontBold = Preferences.getBoolean(Prefkey.boldHuruf, false)? Typeface.BOLD: Typeface.NORMAL;
		}
		
		//# configure text color, red text color, bg color, and verse color
		{
			if (Preferences.getBoolean(Prefkey.is_night_mode, false)) {
				applied.fontColor = Preferences.getInt(App.context.getString(R.string.pref_textColor_night_key), App.context.getResources().getInteger(R.integer.pref_textColor_night_default));
				applied.backgroundColor = Preferences.getInt(App.context.getString(R.string.pref_backgroundColor_night_key), App.context.getResources().getInteger(R.integer.pref_backgroundColor_night_default));
				applied.verseNumberColor = Preferences.getInt(App.context.getString(R.string.pref_verseNumberColor_night_key), App.context.getResources().getInteger(R.integer.pref_verseNumberColor_night_default));
				applied.fontRedColor = Preferences.getInt(App.context.getString(R.string.pref_redTextColor_night_key), App.context.getResources().getInteger(R.integer.pref_redTextColor_night_default));
			} else {
				applied.fontColor = Preferences.getInt(App.context.getString(R.string.pref_textColor_key), App.context.getResources().getInteger(R.integer.pref_textColor_default));
				applied.backgroundColor = Preferences.getInt(App.context.getString(R.string.pref_backgroundColor_key), App.context.getResources().getInteger(R.integer.pref_backgroundColor_default));
				applied.verseNumberColor = Preferences.getInt(App.context.getString(R.string.pref_verseNumberColor_key), App.context.getResources().getInteger(R.integer.pref_verseNumberColor_default));
				applied.fontRedColor = Preferences.getInt(App.context.getString(R.string.pref_redTextColor_key), App.context.getResources().getInteger(R.integer.pref_redTextColor_default));
			}

			// calculation of backgroundColor brightness. Used somewhere else.
			{
				int c = applied.backgroundColor;
				applied.backgroundBrightness = (0.30f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c)) * 0.003921568627f;
			}
		}
		
		Resources res = App.context.getResources();
		
		float scaleBasedOnFontSize = applied.fontSize2dp / 17.f;
		applied.indentParagraphFirst = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.indentParagraphFirst) + 0.5f);
		applied.indentParagraphRest = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.indentParagraphRest) + 0.5f);
		applied.indentSpacing1 = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.indent_1) + 0.5f);
		applied.indentSpacing2 = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.indent_2) + 0.5f);
		applied.indentSpacing3 = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.indent_3) + 0.5f);
		applied.indentSpacing4 = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.indent_4) + 0.5f);
		applied.indentSpacingExtra = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.indentExtra) + 0.5f);
		applied.paragraphSpacingBefore = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.paragraphSpacingBefore) + 0.5f);
		applied.pericopeSpacingTop = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.pericopeSpacingTop) + 0.5f);
		applied.pericopeSpacingBottom = (int) (scaleBasedOnFontSize * res.getDimensionPixelOffset(R.dimen.pericopeSpacingBottom) + 0.5f);
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

	/**
	 * Returns the list of versions that are:
	 * 1. internal
	 * 2. presets that have been DOWNLOADED and ACTIVE
	 * 3. yeses that are ACTIVE
	 **/
	public static Pair<List<String>, List<VersionsActivity.MVersion>> getAvailableVersions() {
		final List<String> options = new ArrayList<>(); // sync with below line
		final List<VersionsActivity.MVersion> data = new ArrayList<>();  // sync with above line

		final VersionConfig c = VersionConfig.get();
		options.add(c.internalLongName); // 1. internal
		data.add(new VersionsActivity.MVersionInternal());

		for (VersionsActivity.MVersionPreset preset: c.presets) { // 2. preset
			if (preset.hasDataFile() && preset.getActive()) {
				options.add(preset.longName);
				data.add(preset);
			}
		}

		// 3. active yeses
		List<VersionsActivity.MVersionYes> yeses = S.getDb().listAllVersions();
		for (VersionsActivity.MVersionYes yes: yeses) {
			if (yes.hasDataFile() && yes.getActive()) {
				options.add(yes.longName);
				data.add(yes);
			}
		}

		return Pair.create(options, data);
	}
}
