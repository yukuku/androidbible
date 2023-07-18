package yuku.alkitab.base.sv;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.widget.Toast;
import androidx.annotation.Keep;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.Date;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.config.VersionConfig;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.versionmanager.VersionListFragment;

public class VersionConfigUpdaterService extends IntentService {
	private static final String TAG = VersionConfigUpdaterService.class.getSimpleName();

	private static final String EXTRA_auto = "auto";

	Handler handler;
	Toast toast;

	public static void checkUpdate(final boolean auto) {
		final Intent intent = new Intent(App.context, VersionConfigUpdaterService.class);
		intent.putExtra(EXTRA_auto, auto);
		App.context.startService(intent);
	}

	public VersionConfigUpdaterService() {
		super(TAG);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		handler = new Handler();
	}

	public void toast(final CharSequence s) {
		handler.post(() -> {
			if (toast == null) {
				toast = Toast.makeText(this, s, Toast.LENGTH_SHORT);
			} else {
				toast.setText(s);
			}

			toast.show();
		});
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			final boolean auto = intent.getBooleanExtra(EXTRA_auto, true);
			try {
				App.getLbm().sendBroadcast(new Intent(VersionListFragment.ACTION_UPDATE_REFRESHING_STATUS).putExtra(VersionListFragment.EXTRA_refreshing, true));
				handleCheckUpdate(auto);
			} finally {
				App.getLbm().sendBroadcast(new Intent(VersionListFragment.ACTION_UPDATE_REFRESHING_STATUS).putExtra(VersionListFragment.EXTRA_refreshing, false));
			}
		}
	}

	@Keep
	static class ModifyTimeJson {
		public boolean success;
		public String message;
		public int modifyTime;
		public String downloadUrl;
	}

	void handleCheckUpdate(final boolean auto) {
		final int lastUpdateCheck = Preferences.getInt(Prefkey.version_config_last_update_check, 0);
		final int now = (int) (System.currentTimeMillis() / 1000L);

		if (auto && lastUpdateCheck != 0 && now > lastUpdateCheck && now - lastUpdateCheck < 7 * 86400) {
			AppLog.d(TAG, "Auto update: no need to check for updates. Last update check: " + new Date(lastUpdateCheck * 1000L) + " now: " + new Date(now * 1000L));
			return;
		}

		final String modifyTimeBody;

		try {
			AppLog.d(TAG, "Downloading list modify time");
			modifyTimeBody = Connections.downloadString(BuildConfig.SERVER_HOST + "versions/list_modify_time?packageName=" + Uri.encode(getPackageName()) + "&versionCode=" + Uri.encode(String.valueOf(App.getVersionCode())));
		} catch (IOException e) {
			AppLog.e(TAG, "failed to download modify time", e);

			if (!auto) {
				toast(getString(R.string.version_config_updater_error_download_modify_time));
			}

			return;
		}

		final ModifyTimeJson modifyTimeObj;
		try {
			modifyTimeObj = App.getDefaultGson().fromJson(modifyTimeBody, ModifyTimeJson.class);
		} catch (JsonSyntaxException e) {
			AppLog.e(TAG, "failed to parse modify time file", e);

			if (!auto) {
				toast(getString(R.string.version_config_updater_error_modify_time_cannot_parse));
			}
			return;
		}

		if (!modifyTimeObj.success) {
			if (!auto) {
				toast(getString(R.string.version_config_updater_error_modify_time_failed, modifyTimeObj.message));
			}
			return;
		}

		final int localModifyTime = Preferences.getInt(Prefkey.version_config_current_modify_time, 0);
		if (localModifyTime != 0 && localModifyTime >= modifyTimeObj.modifyTime) {
			AppLog.d(TAG, "Update: no newer version available. Server modify time: " + new Date(modifyTimeObj.modifyTime * 1000L) + " Local modify time: " + new Date(localModifyTime * 1000L));
			if (!auto) {
				toast(getString(R.string.version_config_updater_no_newer_available));
			}
			return;
		}

		final String versionConfigBody;
		try {
			AppLog.d(TAG, "Downloading version list");
			versionConfigBody = Connections.downloadString(modifyTimeObj.downloadUrl);
		} catch (IOException e) {
			AppLog.e(TAG, "failed to download version list", e);

			if (!auto) {
				toast(getString(R.string.version_config_updater_error_download_list));
			}

			return;
		}

		if (!VersionConfig.isValid(versionConfigBody)) {
			if (!auto) {
				toast(getString(R.string.version_config_updater_error_parsing_list));
			}

			return;
		}

		final boolean updateSuccess = VersionConfig.useLatest(versionConfigBody, modifyTimeObj.modifyTime);
		if (!updateSuccess) {
			if (!auto) {
				toast(getString(R.string.version_config_cannot_write_updated_list));
			}
			return;
		}

		if (!auto) {
			toast(getString(R.string.version_config_updater_updated));
		}

		Preferences.setInt(Prefkey.version_config_last_update_check, now);
		App.getLbm().sendBroadcast(new Intent(VersionListFragment.ACTION_RELOAD));
	}
}
