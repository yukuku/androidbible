package yuku.alkitab.base.sync;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.gson.Gson;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.storage.Prefkey;

import java.util.HashMap;

/**
 * Class that helps record sync events and status.
 */
public class SyncRecorder {
	public enum EventType {
		login(1),
		logout(2),
		;

		int code;

		EventType(final int code) {
			this.code = code;
		}
	}

	public static void log(@NonNull final EventType type, @Nullable final String syncSetName, final Object... kvpairs) {

	}

	static class LastSyncInfoEntryJson {
		public int successTime;
	}

	static class LastSyncInfosJson extends HashMap<String, LastSyncInfoEntryJson> {
	}

	public static void saveLastSuccessTime(@NonNull final String syncSetName, final int successTime) {
		final Gson gson = new Gson();
		final String infos_s = Preferences.getString(Prefkey.sync_last_infos);
		final LastSyncInfosJson obj;
		if (infos_s == null) {
			obj = new LastSyncInfosJson();
		} else {
			obj = gson.fromJson(infos_s, LastSyncInfosJson.class);
		}

		if (successTime == 0) {
			obj.remove(syncSetName);
		} else {
			final LastSyncInfoEntryJson entry = new LastSyncInfoEntryJson();
			entry.successTime = successTime;
			obj.put(syncSetName, entry);
		}

		Preferences.setString(Prefkey.sync_last_infos, gson.toJson(obj));
	}

	public static int getLastSuccessTime(@NonNull final String syncSetName) {
		final Gson gson = new Gson();
		final String infos_s = Preferences.getString(Prefkey.sync_last_infos);
		if (infos_s == null) {
			return 0;
		}

		final LastSyncInfosJson obj = gson.fromJson(infos_s, LastSyncInfosJson.class);
		final LastSyncInfoEntryJson entry = obj.get(syncSetName);
		if (entry == null) {
			return 0;
		}

		return entry.successTime;
	}

	public static void removeAllLastSuccessTimes() {
		Preferences.remove(Prefkey.sync_last_infos);
	}
}
