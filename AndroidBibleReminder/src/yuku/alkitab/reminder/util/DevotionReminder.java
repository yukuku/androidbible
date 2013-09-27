package yuku.alkitab.reminder.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import yuku.afw.storage.Preferences;
import yuku.alkitab.reminder.App;
import yuku.alkitab.reminder.R;
import yuku.alkitab.reminder.br.DevotionReminderReceiver;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class DevotionReminder {
	public static final String TAG = DevotionReminder.class.getSimpleName();

	public static final String REMINDER_TIME = "reminder_time";
	public static final String REMINDER_SOUND = "reminder_sound";
	public static final String REMINDER_VIBRATE = "reminder_vibrate";
	public static final String REMINDER_TEXT = "reminder_text";

	public static void scheduleAlarm(Context context) {
		String reminder_time = Preferences.getString(REMINDER_TIME);
		String reminder_sound = Preferences.getString(REMINDER_SOUND);
		boolean reminder_vibrate = Preferences.getBoolean(REMINDER_VIBRATE, false);

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

	public static String getNotificationText() {
		String notificationText = Preferences.getString(REMINDER_TEXT);
		if (TextUtils.isEmpty(notificationText)) {
			notificationText = App.context.getString(R.string.dr_notification_text);
		}
		return notificationText;
	}
}
