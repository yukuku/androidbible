package yuku.alkitab.base.util;

import android.support.annotation.Nullable;
import yuku.afw.App;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.model.MVersionInternal;
import yuku.alkitab.base.model.VersionImpl;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.IntArrayList;
import yuku.bintex.BintexReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;

/**
 * Handles list of predefined daily verse aris.
 * Also handles saved user settings about widgets.
 */
public abstract class DailyVerseData {
	static final String TAG = DailyVerseData.class.getSimpleName();

	private static IntArrayList dailyVerses;

	public static class SavedState {
		public String versionId;
		public boolean darkText;
		public boolean hideAppIcon;
		public float textSize;
		/** Legacy */
		public boolean transparentBackground;
		public int backgroundAlpha; // 0 to 255
		public int click;
	}

	/**
	 * Get list of ari for the specified parameter.
	 * @param savedState WILL be changed AND SAVED in case of unavailable verses.
	 * @param version to be checked to know whether we have the requested aris
	 * @param direction try increasing {@link yuku.alkitab.base.util.DailyVerseData.SavedState#click} or decrease, according to this direction
	 * @return list of ari
	 */
	@Nullable public static int[] getAris(int appWidgetId, final SavedState savedState, final Version version, final int direction) {
		final IntArrayList allDailyVerses = listAllDailyVerses();
		final int size = allDailyVerses.size();

		boolean savedStateChanged = false;

		int[] aris = null;

		final int maxTries = 20;
		for (int trial = 0; trial < maxTries; trial++) {
			final int index = getIndexFromSeed(appWidgetId, size, savedState.click);
			final int encoded = allDailyVerses.get(index);
			final int verseCount = encoded & 0xff;
			aris = new int[verseCount];
			aris[0] = encoded >>> 8;
			for (int i = 1; i < verseCount; i++) {
				aris[i] = aris[i - 1] + 1;
			}

			// all verses must be available on the specified version
			boolean allAvailable = true;
			for (final int ari : aris) {
				final String verseText = version.loadVerseText(ari);
				if (verseText == null) {
					allAvailable = false;
					break;
				}
			}

			if (!allAvailable) {
				AppLog.d(TAG, "ari 0x" + Integer.toHexString(aris[0]) + " verseCount=" + verseCount + " click=" + savedState.click + " are not available in version " + savedState.versionId);
				if (trial != maxTries - 1) {
					savedState.click += direction;
					savedStateChanged = true;
				}
				aris = null; // remove unsuccessful attempt
			} else {
				break;
			}
		}

		if (savedStateChanged) {
			saveSavedState(appWidgetId, savedState);
		}

		return aris;
	}

	/**
	 * Get index of the element of the array of all predefined daily verses.
	 * @param appWidgetId seed
	 * @param size seed
	 * @param click seed
	 */
	private static int getIndexFromSeed(final int appWidgetId, final int size, final int click) {
		final Calendar calendar = GregorianCalendar.getInstance();
		final long year = calendar.get(Calendar.YEAR);
		final long day = calendar.get(Calendar.DAY_OF_YEAR);
		final long fifteensecs = BuildConfig.DEBUG ? (calendar.get(Calendar.HOUR_OF_DAY) * 240 + calendar.get(Calendar.MINUTE) * 4 + calendar.get(Calendar.SECOND) / 15) : 0;
		final long randomDay = (((year - 1900) << 9) | day) + fifteensecs;
		final long seed = (appWidgetId << 20) | (randomDay + click);
		final Random r = new Random(seed);
		return r.nextInt(size);
	}

	private static IntArrayList listAllDailyVerses() {
		if (dailyVerses == null) {
			dailyVerses = new IntArrayList();
			try {
				InputStream is = App.context.getResources().openRawResource(R.raw.daily_verses_bt);
				BintexReader br = new BintexReader(is);
				while (true) {
					int ari = br.readInt();
					if (ari == -1) {
						break;
					}
					final int verseCount = br.readUint8();
					dailyVerses.add(ari << 8 | verseCount);
				}
				br.close();
			} catch (IOException e) {
				throw new RuntimeException("Error reading daily verses", e);
			}
		}
		return dailyVerses;
	}

	public static Version getVersion(String versionId) {
		if (versionId == null) {
			return VersionImpl.getInternalVersion();
		}

		if (MVersionInternal.getVersionInternalId().equals(versionId)) {
			return VersionImpl.getInternalVersion();
		}

		// try database versions
		for (final MVersionDb mvDb : S.getDb().listAllVersions()) {
			if (mvDb.getVersionId().equals(versionId)) {
				if (mvDb.hasDataFile()) {
					return mvDb.getVersion();
				} else {
					break;
				}
			}
		}

		AppLog.w(TAG, "Version selected for app widget: " + versionId + " is no longer available. Reverting to internal version.");
		return VersionImpl.getInternalVersion();
	}

	public static SavedState loadSavedState(final int appWidgetId) {
		final SavedState res = new SavedState();
		res.darkText = Preferences.getBoolean("app_widget_" + appWidgetId + "_option_dark_text", false);
		res.hideAppIcon = Preferences.getBoolean("app_widget_" + appWidgetId + "_option_hide_app_icon", false);
		res.textSize = Preferences.getFloat("app_widget_" + appWidgetId + "_option_text_size", 14.f);
		res.transparentBackground = Preferences.getBoolean("app_widget_" + appWidgetId + "_option_transparent_background", false);
		res.versionId = Preferences.getString("app_widget_" + appWidgetId + "_version");
		res.click = Preferences.getInt("app_widget_" + appWidgetId + "_click", 0);
		res.backgroundAlpha = Preferences.getInt("app_widget_" + appWidgetId + "_option_backgroundAlpha", 255);
		return res;
	}

	/**
	 * @param savedState null to delete saved state for the specified appWidgetId.
	 */
	public static void saveSavedState(final int appWidgetId, final SavedState savedState) {
		Preferences.hold();
		try {
			if (savedState == null) {
				Preferences.remove("app_widget_" + appWidgetId + "_option_dark_text");
				Preferences.remove("app_widget_" + appWidgetId + "_option_hide_app_icon");
				Preferences.remove("app_widget_" + appWidgetId + "_option_text_size");
				Preferences.remove("app_widget_" + appWidgetId + "_option_transparent_background");
				Preferences.remove("app_widget_" + appWidgetId + "_version");
				Preferences.remove("app_widget_" + appWidgetId + "_click");
				Preferences.remove("app_widget_" + appWidgetId + "_option_backgroundAlpha");
			} else {
				Preferences.setBoolean("app_widget_" + appWidgetId + "_option_dark_text", savedState.darkText);
				Preferences.setBoolean("app_widget_" + appWidgetId + "_option_hide_app_icon", savedState.hideAppIcon);
				Preferences.setFloat("app_widget_" + appWidgetId + "_option_text_size", savedState.textSize);
				Preferences.setBoolean("app_widget_" + appWidgetId + "_option_transparent_background", savedState.transparentBackground);
				Preferences.setString("app_widget_" + appWidgetId + "_version", savedState.versionId);
				Preferences.setInt("app_widget_" + appWidgetId + "_click", savedState.click);
				Preferences.setInt("app_widget_" + appWidgetId + "_option_backgroundAlpha", savedState.backgroundAlpha);
			}
		} finally {
			Preferences.unhold();
		}
	}
}
