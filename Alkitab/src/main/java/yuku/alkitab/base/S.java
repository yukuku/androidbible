package yuku.alkitab.base;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.model.MVersion;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.model.MVersionInternal;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.base.storage.InternalDbHelper;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.storage.SongDb;
import yuku.alkitab.base.storage.SongDbHelper;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;

import java.util.ArrayList;
import java.util.Collections;
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

	/**
	 * Returns the list of versions that are:
	 * 1. internal, or
	 * 2. database versions that have the data file and active
	 **/
	public static List<MVersion> getAvailableVersions() {
		final List<MVersion> res = new ArrayList<>();

		// 1. Internal version
		res.add(S.getMVersionInternal());

		// 2. Database versions
		for (MVersionDb mvDb: S.getDb().listAllVersions()) {
			if (mvDb.hasDataFile() && mvDb.getActive()) {
				res.add(mvDb);
			}
		}

		// sort based on ordering
		Collections.sort(res, (lhs, rhs) -> lhs.ordering - rhs.ordering);

		return res;
	}

	/**
	 * Get the internal version model. This does not return a singleton. The ordering is the latest taken from preferences.
	 */
	public static MVersionInternal getMVersionInternal() {
		final AppConfig ac = AppConfig.get();
		final MVersionInternal res = new MVersionInternal();
		res.locale = ac.internalLocale;
		res.shortName = ac.internalShortName;
		res.longName = ac.internalLongName;
		res.description = null;
		res.ordering = Preferences.getInt(Prefkey.internal_version_ordering, MVersionInternal.DEFAULT_ORDERING);
		return res;
	}

	public interface VersionDialogListener {
		void onVersionSelected(MVersion mv);
	}

	public static void openVersionsDialog(final Activity activity, final boolean withNone, final String selectedVersionId, final VersionDialogListener listener) {
		final List<MVersion> versions = getAvailableVersions();

		if (withNone) {
			versions.add(0, null);
		}

		// determine the currently selected one
		int selected = -1;
		if (withNone && selectedVersionId == null) {
			selected = 0; // "none"
		} else {
			for (int i = (withNone? 1: 0) /* because 0 is None */; i < versions.size(); i++) {
				final MVersion mv = versions.get(i);
				if (mv.getVersionId().equals(selectedVersionId)) {
					selected = i;
					break;
				}
			}
		}

		final String[] options = new String[versions.size()];
		for (int i = 0; i < versions.size(); i++) {
			final MVersion version = versions.get(i);
			options[i] = version == null ? activity.getString(R.string.split_version_none) : version.longName;
		}

		new MaterialDialog.Builder(activity)
			.items(options)
			.itemsCallbackSingleChoice(selected, (dialog, view, which, text) -> {
				final MVersion mv = versions.get(which);
				listener.onVersionSelected(mv);
				dialog.dismiss();
			})
			.alwaysCallSingleChoiceCallback()
			.positiveText(R.string.versi_lainnya)
			.callback(new MaterialDialog.ButtonCallback() {
				@Override
				public void onPositive(final MaterialDialog dialog) {
					activity.startActivity(VersionsActivity.createIntent());
				}
			})
			.show();
	}

	public static String getVersionInitials(final Version version) {
		final String shortName = version.getShortName();
		if (shortName != null) {
			return shortName;
		} else {
			final String longName = version.getLongName();
			if (longName.length() <= 6) {
				return longName.toUpperCase();
			}

			// try to get the first letter of each word
			final String[] words = longName.split("[^A-Za-z0-9]+");
			final char[] chars = new char[words.length];
			int cnt = 0;
			for (int i = 0; i < chars.length; i++) {
				if (words[i].length() > 0) {
					chars[cnt++] = Character.toUpperCase(words[i].charAt(0));
				}
			}
			return new String(chars, 0, cnt);
		}
	}
}
