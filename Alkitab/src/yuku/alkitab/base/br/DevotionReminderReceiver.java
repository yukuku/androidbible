package yuku.alkitab.base.br;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.ac.DevotionActivity;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class DevotionReminderReceiver extends BroadcastReceiver {
	public static final String TAG = DevotionReminderReceiver.class.getSimpleName();

	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "@@onReceive");
		Log.d(TAG, "- intent data = " + intent.getDataString());

		PendingIntent pi = PendingIntent.getActivity(context, 0, new Intent(context, DevotionActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder b = new NotificationCompat.Builder(context)
		.setAutoCancel(true)
		.setContentText(context.getString(R.string.dr_notification_text))
		.setContentTitle(context.getString(R.string.dr_notification_title))
		.setContentIntent(pi)
		.setSmallIcon(R.drawable.ic_launcher);

		String reminder_sound = Preferences.getString("reminder_sound");
		boolean reminder_vibrate = Preferences.getBoolean("reminder_vibrate", false);

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

	public static void scheduleAlarm(Context context) {
		String reminder_time = Preferences.getString("reminder_time");
		String reminder_sound = Preferences.getString("reminder_sound");
		boolean reminder_vibrate = Preferences.getBoolean("reminder_vibrate", false);

		Log.d(TAG, "@@scheduleAlarms");
		Log.d(TAG, "- reminder_time: " + reminder_time);
		Log.d(TAG, "- reminder_sound: " + reminder_sound);
		Log.d(TAG, "- reminder_vibrate: " + reminder_vibrate);

		setAlarm(context, reminder_time);
	}

	private static void setAlarm(final Context context, final String reminder_time) {
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(context, DevotionReminderReceiver.class);
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		// always cancel current (if any)
		am.cancel(pi);

		if (TextUtils.isEmpty(reminder_time)) {
			return;
		}

		int hour = Integer.parseInt(reminder_time.substring(0, 2));
		int minute = Integer.parseInt(reminder_time.substring(2, 4));
		Calendar c = GregorianCalendar.getInstance();
		c.set(Calendar.HOUR_OF_DAY, hour);
		c.set(Calendar.MINUTE, minute);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);

		if (c.getTimeInMillis() < System.currentTimeMillis()) {
			c.add(Calendar.DAY_OF_YEAR, 1);
		}

		am.setRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);

		Log.d(TAG, "alarm set at " + c.toString());
	}
}
