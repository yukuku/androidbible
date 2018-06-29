package yuku.alkitab.base.sync;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.AppLog;

import java.util.List;

public class GcmIntentService extends JobIntentService {
	static final String TAG = GcmIntentService.class.getSimpleName();

	/**
	 * Unique job ID for this service.
	 */
	static final int JOB_ID = 16305555;

	/**
	 * Convenience method for enqueuing work in to this service.
	 */
	static void enqueueWork(@NonNull Context context, @NonNull Intent work) {
		enqueueWork(context, GcmIntentService.class, JOB_ID, work);
	}

	@Override
	protected void onHandleWork(@NonNull final Intent intent) {
		handle(intent);
	}

	public static class GcmMessageEncodedDataJson {
		public String kind;
		public List<String> syncSetNames;
	}

	private void handle(@NonNull final Intent intent) {
		final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);

		final Bundle extras = intent.getExtras();
		if (extras == null || extras.isEmpty()) {
			AppLog.e(TAG, "No extras");
			return;
		}

		switch (gcm.getMessageType(intent)) {
			case GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR:
				AppLog.d(TAG, "MESSAGE_TYPE_SEND_ERROR: " + extras.toString());
				break;
			case GoogleCloudMessaging.MESSAGE_TYPE_DELETED:
				AppLog.d(TAG, "MESSAGE_TYPE_DELETED: " + extras.toString());
				break;
			case GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE:
				// It's a regular GCM message, do some work.
				try {
					final String source_installation_id = intent.getStringExtra(Gcm.GCM_MESSAGE_KEY_source_installation_id);
					if (U.equals(source_installation_id, U.getInstallationId())) {
						AppLog.d(TAG, "Gcm message sourced from self is ignored");
					} else {
						final String encoded_data = intent.getStringExtra(Gcm.GCM_MESSAGE_KEY_encoded_data);
						final GcmMessageEncodedDataJson data = App.getDefaultGson().fromJson(encoded_data, GcmMessageEncodedDataJson.class);
						if ("sync".equals(data.kind)) {
							if (data.syncSetNames != null) {
								Sync.notifySyncNeeded(data.syncSetNames.toArray(new String[data.syncSetNames.size()]));
							}
						}
					}
				} catch (Exception e) {
					AppLog.d(TAG, "Exception processing GCM message", e);
				}
				break;
		}
	}
}
