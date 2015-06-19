package yuku.alkitab.base.util;

import android.content.Intent;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.storage.Prefkey;

/**
 * Manages current reading (selected from reading plan) with persistence.
 */
public class CurrentReading {
	public static final String ACTION_CURRENT_READING_CHANGED = CurrentReading.class.getName() + ".action.CURRENT_READING_CHANGED";

	public static void set(final int ari_start, final int ari_end) {
		Preferences.hold();
		try {
			Preferences.setInt(Prefkey.current_reading_ari_start, ari_start);
			Preferences.setInt(Prefkey.current_reading_ari_end, ari_end);
		} finally {
			Preferences.unhold();
		}

		App.getLbm().sendBroadcast(new Intent(ACTION_CURRENT_READING_CHANGED));
	}

	public static void clear() {
		Preferences.hold();
		try {
			Preferences.remove(Prefkey.current_reading_ari_start);
			Preferences.remove(Prefkey.current_reading_ari_end);
		} finally {
			Preferences.unhold();
		}

		App.getLbm().sendBroadcast(new Intent(ACTION_CURRENT_READING_CHANGED));
	}

	/**
	 * @return null if no current reading
	 */
	public static int[] get() {
		if (!Preferences.contains(Prefkey.current_reading_ari_start)) {
			return null;
		}

		return new int[] {
			Preferences.getInt(Prefkey.current_reading_ari_start, 0),
			Preferences.getInt(Prefkey.current_reading_ari_end, 0),
		};
	}
}
