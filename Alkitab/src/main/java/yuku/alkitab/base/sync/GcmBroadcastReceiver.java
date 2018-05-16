package yuku.alkitab.base.sync;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;
import yuku.alkitab.base.App;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;

public class GcmBroadcastReceiver extends WakefulBroadcastReceiver {
	static final String TAG = GcmBroadcastReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		if (BuildConfig.DEBUG) {
			AppLog.d(TAG, "@@onReceive gcm intent: " + intent.toUri(0));
		}

		// Start the service, keeping the device awake while it is launching.
		intent.setClass(App.context, GcmIntentService.class);
		startWakefulService(context, intent);
		setResultCode(Activity.RESULT_OK);
	}
}
