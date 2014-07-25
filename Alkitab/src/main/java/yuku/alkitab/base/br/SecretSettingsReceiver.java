package yuku.alkitab.base.br;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import yuku.alkitab.base.ac.SecretSettingsActivity;

public class SecretSettingsReceiver extends BroadcastReceiver {
	public static final String TAG = SecretSettingsReceiver.class.getSimpleName();

	@Override public void onReceive(Context context, Intent intent) {
		Intent startIntent = new Intent(context, SecretSettingsActivity.class);
		startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(startIntent);
	}
}
