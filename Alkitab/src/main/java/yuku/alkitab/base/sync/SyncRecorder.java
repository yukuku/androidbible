package yuku.alkitab.base.sync;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Sqlitil;

import java.util.HashMap;

/**
 * Class that helps record sync events and status.
 */
public class SyncRecorder {
	public enum EventKind {
		login_attempt(10),
		login_failed(12),
		login_gcm_sending_failed(13),
		login_gcm_not_possessed_yet(14),
		login_success_pre(15),
		login_success_post(16),
		sync_forced(80),
		sync_needed_notified(81),
		sync_adapter_on_perform(82),
		sync_adapter_set_not_enabled(83),
		error_no_simple_token(100),
		current_entities_gathered(101),
		sync_to_server_pre(102),
		sync_to_server_post_response_ok(103),
		sync_to_server_post_error_syntax(110),
		sync_to_server_post_error_io(111),
		sync_to_server_not_success(120),
		sync_to_server_error_append_delta_null(121),
		sync_to_server_got_success_data(122),
		apply_result(140),
		all_succeeded(141),
		logout_pre(200),
		logout_post(201),
		;

		public int code;

		EventKind(final int code) {
			this.code = code;
		}
	}

	static final HashMap<String, Object> reusedMap = new HashMap<>();

	public static void log(@NonNull final EventKind kind, @Nullable final String syncSetName, final Object... kvpairs) {
		final String params;
		if (kvpairs.length == 0) {
			params = null;
		} else {
			synchronized (reusedMap) {
				reusedMap.clear();
				for (int i = 0; i < kvpairs.length; i += 2) {
					final String k = kvpairs[i].toString();
					final Object v = kvpairs[i + 1];
					reusedMap.put(k, v);
				}
				params = App.getDefaultGson().toJson(reusedMap);
			}
		}

		S.getDb().insertSyncLog(Sqlitil.nowDateTime(), kind, syncSetName, params);
	}

	static class LastSyncInfoEntryJson {
		public int successTime;
	}

	static class LastSyncInfosJson extends HashMap<String, LastSyncInfoEntryJson> {
	}

	public static void saveLastSuccessTime(@NonNull final String syncSetName, final int successTime) {
		final String infos_s = Preferences.getString(Prefkey.sync_last_infos);
		final LastSyncInfosJson obj;
		if (infos_s == null) {
			obj = new LastSyncInfosJson();
		} else {
			obj = App.getDefaultGson().fromJson(infos_s, LastSyncInfosJson.class);
		}

		if (successTime == 0) {
			obj.remove(syncSetName);
		} else {
			final LastSyncInfoEntryJson entry = new LastSyncInfoEntryJson();
			entry.successTime = successTime;
			obj.put(syncSetName, entry);
		}

		Preferences.setString(Prefkey.sync_last_infos, App.getDefaultGson().toJson(obj));
	}

	public static int getLastSuccessTime(@NonNull final String syncSetName) {
		final String infos_s = Preferences.getString(Prefkey.sync_last_infos);
		if (infos_s == null) {
			return 0;
		}

		final LastSyncInfosJson obj = App.getDefaultGson().fromJson(infos_s, LastSyncInfosJson.class);
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
