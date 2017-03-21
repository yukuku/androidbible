package yuku.alkitab.reminder.br;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.res.ResourcesCompat;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.debug.R;
import yuku.alkitab.reminder.util.DevotionReminder;

public class DevotionReminderReceiver extends BroadcastReceiver {
	public static final String TAG = DevotionReminderReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {

		PendingIntent pi = PendingIntent.getActivity(context, 0, DevotionActivity.createIntent(), PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder b = new NotificationCompat.Builder(context)
			.setAutoCancel(true)
			.setContentText(DevotionReminder.getNotificationText())
			.setContentTitle(context.getString(R.string.dr_notification_title))
			.setContentIntent(pi)
			.setColor(ResourcesCompat.getColor(context.getResources(), R.color.accent, context.getTheme()))
			.setSmallIcon(R.drawable.ic_stat_reminder);

		String reminder_sound = Preferences.getString(DevotionReminder.REMINDER_SOUND);
		boolean reminder_vibrate = Preferences.getBoolean(DevotionReminder.REMINDER_VIBRATE, false);

		if (reminder_sound == null) {
			b.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
		} else if (reminder_sound.length() == 0) { // None/silend
			// Do nothing
		} else {
			b.setSound(Uri.parse(reminder_sound));
		}

		if (reminder_vibrate) {
			long[] pattern = {500,500,500,500,500,500,500,500,500};
			b.setVibrate(pattern);
		}

		Notification n = b.build();

		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(R.id.NOTIF_reminder, n);
	}
}
