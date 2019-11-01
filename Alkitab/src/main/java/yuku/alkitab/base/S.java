package yuku.alkitab.base;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class S {
	static final String TAG = S.class.getSimpleName();

	/**
	 * Values applied from settings, so we do not need to calculate it many times.
	 */
	public static class CalculatedDimensions {
		/**
		 * in dp
		 */
		public float fontSize2dp;

		public Typeface fontFace;
		public float lineSpacingMult;
		public int fontBold;

		public int fontColor;
		public int fontRedColor;
		public int backgroundColor;
		public int verseNumberColor;

		/**
		 * 0.f to 1.f
		 */
		public float backgroundBrightness;

		// everything below is in px
		public int indentParagraphFirst;
		public int indentParagraphRest;
		public int indentSpacing1;
		public int indentSpacing2;
		public int indentSpacing3;
		public int indentSpacing4;
		public int indentSpacingExtra;
		public int paragraphSpacingBefore;
		public int pericopeSpacingTop;
		public int pericopeSpacingBottom;
	}

	private static class CalculatedDimensionsHolder {
		static CalculatedDimensions applied;

		static {
			if (applied == null) {
				applied = calculateDimensionsFromPreferences();
			}
		}
	}

	@NonNull
	public static CalculatedDimensions applied() {
		return CalculatedDimensionsHolder.applied;
	}

	// The process-global active Bible version.
	private static class ActiveVersionHolder {
		static MVersion activeMVersion;
		static Version activeVersion;
		static String activeVersionId;

		static {
			// Load the version we want before everything, so we do not load it multiple times.
			final String lastVersionId = Preferences.getString(Prefkey.lastVersionId);
			final MVersion mv = getVersionFromVersionId(lastVersionId);
			final MVersion actual = mv == null ? getMVersionInternal() : mv;
			setActiveVersion(actual);
		}

		public synchronized static void setActiveVersion(final MVersion mv) {
			final Version version = mv.getVersion();
			final String versionId = mv.getVersionId();
			activeMVersion = mv;

			final Throwable trace = BuildConfig.DEBUG ? new Throwable().fillInStackTrace() : null;
			AppLog.d(TAG, "@@setActiveVersion version=" + version + " versionId=" + versionId, trace);

			activeVersion = version;
			activeVersionId = versionId;
		}
	}

	@NonNull
	public static MVersion activeMVersion() {
		return ActiveVersionHolder.activeMVersion;
	}

	@NonNull
	public static Version activeVersion() {
		return ActiveVersionHolder.activeVersion;
	}

	@NonNull
	public static String activeVersionId() {
		return ActiveVersionHolder.activeVersionId;
	}

	public synchronized static void setActiveVersion(final MVersion mv) {
		ActiveVersionHolder.setActiveVersion(mv);
	}

	/**
	 * @return null when the specified versionId is not found OR we really want internal.
	 */
	@Nullable
	public static MVersion getVersionFromVersionId(String versionId) {
		if (versionId == null || MVersionInternal.getVersionInternalId().equals(versionId)) {
			return null; // internal is made the same as null
		}

		// let's look at yes versions
		for (MVersionDb mvDb : getDb().listAllVersions()) {
			if (mvDb.getVersionId().equals(versionId)) {
				if (mvDb.hasDataFile()) {
					return mvDb;
				} else {
					// the data file is not available
					return null;
				}
			}
		}

		return null; // not known
	}

	public static void recalculateAppliedValuesBasedOnPreferences() {
		CalculatedDimensionsHolder.applied = calculateDimensionsFromPreferences();
	}

	@NonNull
	private static CalculatedDimensions calculateDimensionsFromPreferences() {
		final CalculatedDimensions res = new CalculatedDimensions();

		final Resources resources = App.context.getResources();

		//# configure font size
		{
			res.fontSize2dp = Preferences.getFloat(Prefkey.ukuranHuruf2, (float) resources.getInteger(R.integer.pref_ukuranHuruf2_default));
		}

		//# configure fonts
		{
			res.fontFace = FontManager.typeface(Preferences.getString(Prefkey.jenisHuruf, null));
			res.lineSpacingMult = Preferences.getFloat(Prefkey.lineSpacingMult, 1.15f);
			res.fontBold = Preferences.getBoolean(Prefkey.boldHuruf, false) ? Typeface.BOLD : Typeface.NORMAL;
		}

		//# configure text color, red text color, bg color, and verse color
		{
			if (Preferences.getBoolean(Prefkey.is_night_mode, false)) {
				res.fontColor = Preferences.getInt(R.string.pref_textColor_night_key, R.integer.pref_textColor_night_default);
				res.backgroundColor = Preferences.getInt(R.string.pref_backgroundColor_night_key, R.integer.pref_backgroundColor_night_default);
				res.verseNumberColor = Preferences.getInt(R.string.pref_verseNumberColor_night_key, R.integer.pref_verseNumberColor_night_default);
				res.fontRedColor = Preferences.getInt(R.string.pref_redTextColor_night_key, R.integer.pref_redTextColor_night_default);
			} else {
				res.fontColor = Preferences.getInt(R.string.pref_textColor_key, R.integer.pref_textColor_default);
				res.backgroundColor = Preferences.getInt(R.string.pref_backgroundColor_key, R.integer.pref_backgroundColor_default);
				res.verseNumberColor = Preferences.getInt(R.string.pref_verseNumberColor_key, R.integer.pref_verseNumberColor_default);
				res.fontRedColor = Preferences.getInt(R.string.pref_redTextColor_key, R.integer.pref_redTextColor_default);
			}

			// calculation of backgroundColor brightness. Used somewhere else.
			{
				int c = res.backgroundColor;
				res.backgroundBrightness = (0.30f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c)) * 0.003921568627f;
			}
		}

		float scaleBasedOnFontSize = res.fontSize2dp / 17.f;
		res.indentParagraphFirst = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indentParagraphFirst) + 0.5f);
		res.indentParagraphRest = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indentParagraphRest) + 0.5f);
		res.indentSpacing1 = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indent_1) + 0.5f);
		res.indentSpacing2 = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indent_2) + 0.5f);
		res.indentSpacing3 = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indent_3) + 0.5f);
		res.indentSpacing4 = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indent_4) + 0.5f);
		res.indentSpacingExtra = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indentExtra) + 0.5f);
		res.paragraphSpacingBefore = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.paragraphSpacingBefore) + 0.5f);
		res.pericopeSpacingTop = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.pericopeSpacingTop) + 0.5f);
		res.pericopeSpacingBottom = (int) (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.pericopeSpacingBottom) + 0.5f);
		return res;
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
		res.add(getMVersionInternal());

		// 2. Database versions
		for (MVersionDb mvDb : getDb().listAllVersions()) {
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
	@NonNull
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

	public static void openVersionsDialog(@NonNull final Activity activity, final boolean withNone, @Nullable final String selectedVersionId, @NonNull final VersionDialogListener listener) {
		final List<MVersion> versions = getAvailableVersions();

		if (withNone) {
			versions.add(0, null);
		}

		// determine the currently selected one
		int selected = -1;
		if (withNone && selectedVersionId == null) {
			selected = 0; // "none"
		} else {
			for (int i = (withNone ? 1 : 0) /* because 0 is None */; i < versions.size(); i++) {
				final MVersion mv = versions.get(i);
				if (mv.getVersionId().equals(selectedVersionId)) {
					selected = i;
					break;
				}
			}
		}

		final CharSequence[] options = new CharSequence[versions.size()];
		for (int i = 0; i < versions.size(); i++) {
			final MVersion version = versions.get(i);
			options[i] = version == null ? activity.getString(R.string.split_version_none) : version.longName;
		}

		new MaterialDialog.Builder(activity)
			.items(options)
			.itemsCallbackSingleChoice(selected, (dialog, view, which, text) -> {
				if (which == -1) {
					// it is possible that 'which' is -1 in the case that
					// a version is already deleted, but the current displayed version is that version
					// (hence the initial selected item position is -1) and then the user
					// presses the "other version" button. This callback will still be triggered
					// before the positive button callback.
				} else {
					final MVersion mv = versions.get(which);
					listener.onVersionSelected(mv);
					dialog.dismiss();
				}
				return true;
			})
			.alwaysCallSingleChoiceCallback()
			.positiveText(R.string.versi_lainnya)
			.onPositive((dialog, which) -> activity.startActivity(VersionsActivity.createIntent()))
			.show();
	}
}
