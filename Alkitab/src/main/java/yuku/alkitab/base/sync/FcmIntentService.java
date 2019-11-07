package yuku.alkitab.base.sync;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.AppLog;

import java.util.List;
import yuku.alkitab.base.util.InstallationUtil;

public class FcmIntentService extends JobIntentService {
	static final String TAG = FcmIntentService.class.getSimpleName();

	public static final String FCM_MESSAGE_KEY_source_installation_id = "source_installation_id";
	public static final String FCM_MESSAGE_KEY_encoded_data = "encoded_data";

	/**
	 * Unique job ID for this service.
	 */
	static final int JOB_ID = 16305555;

	/**
	 * Convenience method for enqueuing work in to this service.
	 */
	static void enqueueWork(@NonNull Context context, @NonNull Intent work) {
		enqueueWork(context, FcmIntentService.class, JOB_ID, work);
	}

	@Override
	protected void onHandleWork(@NonNull final Intent intent) {
		handle(intent);
	}

	@Keep
	public static class FcmMessageEncodedDataJson {
		public String kind;
		public List<String> syncSetNames;
	}

	private void handle(@NonNull final Intent intent) {
		final Bundle extras = intent.getExtras();
		if (extras == null || extras.isEmpty()) {
			AppLog.e(TAG, "No extras");
			return;
		}

		try {
			final String source_installation_id = intent.getStringExtra(FCM_MESSAGE_KEY_source_installation_id);
			if (U.equals(source_installation_id, InstallationUtil.getInstallationId())) {
				AppLog.d(TAG, "FCM message sourced from self is ignored");
			} else {
				final String encoded_data = intent.getStringExtra(FCM_MESSAGE_KEY_encoded_data);
				final FcmMessageEncodedDataJson data = App.getDefaultGson().fromJson(encoded_data, FcmMessageEncodedDataJson.class);
				if ("sync".equals(data.kind)) {
					if (data.syncSetNames != null) {
						Sync.notifySyncNeeded(data.syncSetNames.toArray(new String[data.syncSetNames.size()]));
					}
				}
			}
		} catch (Exception e) {
			AppLog.d(TAG, "Exception processing FCM message", e);
		}
	}
}
