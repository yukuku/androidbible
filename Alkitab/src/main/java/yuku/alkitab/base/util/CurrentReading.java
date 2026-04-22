package yuku.alkitab.base.util;

import yuku.afw.storage.Preferences;
import yuku.alkitab.base.events.AppEvents;
import yuku.alkitab.base.storage.Prefkey;

/**
 * Manages current reading (selected from reading plan) with persistence.
 */
public class CurrentReading {
	public static void set(final int ari_start, final int ari_end) {
		Preferences.withTransaction(() -> {
			Preferences.setInt(Prefkey.current_reading_ari_start, ari_start);
			Preferences.setInt(Prefkey.current_reading_ari_end, ari_end);
		});

		AppEvents.emitCurrentReadingChanged();
	}

	public static void clear() {
		Preferences.withTransaction(() -> {
			Preferences.remove(Prefkey.current_reading_ari_start);
			Preferences.remove(Prefkey.current_reading_ari_end);
		});

		AppEvents.emitCurrentReadingChanged();
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
