package yuku.alkitab.base.sync;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;

public class GcmBroadcastReceiver extends BroadcastReceiver {
	static final String TAG = GcmBroadcastReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		if (BuildConfig.DEBUG) {
			AppLog.d(TAG, "@@onReceive gcm intent: " + intent.toUri(0));
		}

		intent.setClass(context, GcmIntentService.class);
		GcmIntentService.enqueueWork(context, intent);
		setResultCode(Activity.RESULT_OK);
	}
}
