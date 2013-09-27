package yuku.alkitab.reminder.br;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import yuku.afw.storage.Preferences;
import yuku.alkitab.reminder.R;
import yuku.alkitab.reminder.util.DevotionReminder;

public class DevotionReminderReceiver extends BroadcastReceiver {
	public static final String TAG = DevotionReminderReceiver.class.getSimpleName();

	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "@@onReceive");
		Log.d(TAG, "- intent data = " + intent.getDataString());

		PendingIntent pi = PendingIntent.getActivity(context, 0, new Intent("yuku.alkitab.ACTION_OPEN_DEVOTION"), PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder b = new NotificationCompat.Builder(context)
		.setAutoCancel(true)
		.setContentText(DevotionReminder.getNotificationText())
		.setContentTitle(context.getString(R.string.dr_notification_title))
		.setContentIntent(pi)
		.setSmallIcon(R.drawable.ic_stat_reminder);

		String reminder_sound = Preferences.getString(DevotionReminder.REMINDER_SOUND);
		boolean reminder_vibrate = Preferences.getBoolean(DevotionReminder.REMINDER_VIBRATE, false);

		if (reminder_sound != null) {
			b.setSound(Uri.parse(reminder_sound));
		}

		if (reminder_vibrate) {
			long[] pattern = {500,500,500,500,500,500,500,500,500};
			b.setVibrate(pattern);
		}

		Notification n = b.build();

		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(0, n);

	}


}
