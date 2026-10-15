package yuku.alkitab.base.sync;

import android.util.SparseArray;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Sqlitil;

/**
 * Class that helps record sync events and status.
 */
public class SyncRecorder {

	// used as color constants by EventKind
	static final int OK = 0xff339933;
	static final int INFO = 0xff444444;
	static final int NORMAL_RESULT = 0xff888844;
	static final int ERROR = 0xff993333;

	public enum EventKind {
		diagnostic_stage(1, INFO),
		diagnostic_error(2, ERROR),
		schedule_requested(3, INFO),
		schedule_accepted(4, INFO),
		sync_skipped(5, INFO),
		worker_stopped(6, ERROR),
		http_response(7, INFO),
		login_attempt(10, INFO),
		register_attempt(11, INFO),
		login_failed(12, ERROR),
		login_fcm_sending_failed(13, ERROR),
		login_fcm_not_possessed_yet(14, NORMAL_RESULT),
		login_success_pre(15, OK),
		login_success_post(16, OK),
		register_failed(17, ERROR),
		fcm_send_attempt(20, INFO),
		fcm_send_success(21, INFO),
		fcm_send_not_success(22, ERROR),
		fcm_send_error_io(25, ERROR),
		fcm_send_error_json(26, ERROR),
		sync_forced(80, INFO),
		sync_needed_notified(81, INFO),
		sync_adapter_on_perform(82, INFO),
		sync_adapter_set_not_enabled(83, INFO),
		partial_sync_info(84, INFO),
		error_no_simple_token(100, ERROR),
		current_entities_gathered(101, INFO),
		sync_to_server_pre(102, INFO),
		sync_to_server_post_response_ok(103, OK),
		sync_to_server_post_error_syntax(110, ERROR),
		sync_to_server_post_error_io(111, ERROR),
		sync_adapter_error_unexpected(112, ERROR),
		sync_to_server_not_success(120, ERROR),
		sync_to_server_error_append_delta_null(121, ERROR),
		sync_to_server_got_success_data(122, INFO),
		apply_result(140, INFO),
		all_succeeded(141, OK),
		worker_result_failed(150, ERROR),
		worker_result_succeeded(160, OK),
		worker_result_succeeded_no_work_needed(161, OK),
		worker_is_currently_running(170, INFO),
		logout_pre(200, INFO),
		logout_post(201, OK),
		;

		public final int code;
		public final int backgroundColor;

		EventKind(final int code, final int backgroundColor) {
			this.code = code;
			this.backgroundColor = backgroundColor;
		}

		static final SparseArray<EventKind> codeIndex = new SparseArray<>();

		static {
			for (final EventKind kind : values()) {
				codeIndex.put(kind.code, kind);
			}
		}

		public static EventKind fromCode(final int code) {
			return codeIndex.get(code);
		}
	}

    private static final java.util.ArrayDeque<yuku.alkitab.base.model.SyncLog> fallback = new java.util.ArrayDeque<>();

    public static void log(@NonNull final EventKind kind, @Nullable final String syncSetName, final Object... kvpairs) {
        final java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kvpairs.length && values.size() < SyncDiagnostics.MAX_FIELDS; i += 2) {
            if (kvpairs[i] instanceof String) values.put((String) kvpairs[i], kvpairs[i + 1]);
        }
        final java.util.Map<String, Object> safe = SyncDiagnostics.safeParams(values);
        final String set = SyncDiagnostics.safeSet(syncSetName);
        final int time = Sqlitil.nowDateTime();
        try {
            App.services.storage.getDb().insertSyncLog(time, kind, set, App.getDefaultGson().toJson(safe));
        } catch (RuntimeException error) {
            // A database failure must neither break sync nor hide its diagnostics.
            final yuku.alkitab.base.model.SyncLog row = new yuku.alkitab.base.model.SyncLog();
            row.createTime = Sqlitil.toDate(time);
            row.kind_code = kind.code;
            row.syncSetName = set;
            row.params = safe;
            synchronized (SyncRecorder.class) {
                if (fallback.size() >= 50) fallback.removeFirst();
                fallback.addLast(row);
            }
            android.util.Log.w("SyncRecorder", "Diagnostic storage unavailable: " + SyncDiagnostics.causes(error));
        }
    }

    public static synchronized java.util.List<yuku.alkitab.base.model.SyncLog> fallbackLogs() {
        return new java.util.ArrayList<>(fallback);
    }

    public static void error(final String stage, final String set, final Throwable error) {
        log(EventKind.diagnostic_error, set, "stage", stage, "category", SyncDiagnostics.category(error), "causes", SyncDiagnostics.causes(error));
    }

    public static void environment(final EventKind kind, final String workId) {
        log(kind, null, "work_id", workId, "app_version", App.getVersionName(),
            "version_code", App.getVersionCode(), "android", android.os.Build.VERSION.RELEASE,
            "sdk", android.os.Build.VERSION.SDK_INT);
    }

	@Keep
	static class LastSyncInfoEntryJson {
		public int successTime;
	}

	@Keep
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
