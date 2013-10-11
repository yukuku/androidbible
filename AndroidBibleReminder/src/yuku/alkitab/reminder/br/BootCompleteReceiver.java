package yuku.alkitab.reminder.br;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompleteReceiver extends BroadcastReceiver {
	public static final String TAG = BootCompleteReceiver.class.getSimpleName();
	public void onReceive(Context context, Intent intent) {
		//App class will schedule the alarm. We don't need to put anything here since receiving will run the App
	}
}
