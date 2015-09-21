package yuku.alkitab.base.sync;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;

import java.util.List;

public class GcmIntentService extends IntentService {
	static final String TAG = GcmIntentService.class.getSimpleName();

	public GcmIntentService() {
		super("GcmIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		try {
			handle(intent);
		} finally {
			// Release the wake lock provided by the WakefulBroadcastReceiver.
			GcmBroadcastReceiver.completeWakefulIntent(intent);
		}
	}

	public static class GcmMessageEncodedDataJson {
		public String kind;
		public List<String> syncSetNames;
	}

	private void handle(final Intent intent) {
		final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);

		final Bundle extras = intent.getExtras();
		if (extras.isEmpty()) {
			return;
		}

		switch (gcm.getMessageType(intent)) {
			case GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR:
				Log.d(TAG, "MESSAGE_TYPE_SEND_ERROR: " + extras.toString());
				break;
			case GoogleCloudMessaging.MESSAGE_TYPE_DELETED:
				Log.d(TAG, "MESSAGE_TYPE_DELETED: " + extras.toString());
				break;
			case GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE:
				// It's a regular GCM message, do some work.
				try {
					final String source_installation_id = intent.getStringExtra(Gcm.GCM_MESSAGE_KEY_source_installation_id);
					if (U.equals(source_installation_id, U.getInstallationId())) {
						Log.d(TAG, "Gcm message sourced from self is ignored");
					} else {
						final String encoded_data = intent.getStringExtra(Gcm.GCM_MESSAGE_KEY_encoded_data);
						final GcmMessageEncodedDataJson data = App.getDefaultGson().fromJson(encoded_data, GcmMessageEncodedDataJson.class);
						if ("sync".equals(data.kind)) {
							if (data.syncSetNames != null) {
								for (final String syncSetName : data.syncSetNames) {
									Sync.notifySyncNeeded(syncSetName);
								}
							}
						}
					}
				} catch (Exception e) {
					Log.d(TAG, "Exception processing GCM message", e);
				}
				break;
		}
	}
}
