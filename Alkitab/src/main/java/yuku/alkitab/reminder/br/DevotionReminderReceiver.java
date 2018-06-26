package yuku.alkitab.reminder.br;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.res.ResourcesCompat;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.debug.R;
import yuku.alkitab.reminder.util.DevotionReminder;

public class DevotionReminderReceiver extends BroadcastReceiver {
	public static final String TAG = DevotionReminderReceiver.class.getSimpleName();
	static final String NOTIFICATION_CHANNEL_ID = "devotion_reminder";

	@Override
	public void onReceive(Context context, Intent intent) {

		final PendingIntent pi = PendingIntent.getActivity(context, 0, DevotionActivity.createIntent(), PendingIntent.FLAG_UPDATE_CURRENT);

		if (Build.VERSION.SDK_INT >= 26) {
			final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.notification_channel_devotion_reminder_name), NotificationManager.IMPORTANCE_DEFAULT);
			final NotificationManager nm = App.context.getSystemService(NotificationManager.class);
			if (nm != null) nm.createNotificationChannel(channel);
		}

		final NotificationCompat.Builder b = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
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
			long[] pattern = {500, 500, 500, 500, 500, 500, 500, 500, 500};
			b.setVibrate(pattern);
		}

		final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(R.id.NOTIF_reminder, b.build());
	}
}
