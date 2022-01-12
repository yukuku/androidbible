package yuku.alkitab.reminder.br

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import yuku.afw.storage.Preferences
import yuku.alkitab.base.ac.DevotionActivity
import yuku.alkitab.debug.R
import yuku.alkitab.reminder.util.DevotionReminder

private const val NOTIFICATION_CHANNEL_ID = "devotion_reminder"

class DevotionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm: NotificationManager = context.getSystemService() ?: return

        val pi = PendingIntent.getActivity(context, 0, DevotionActivity.createIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.notification_channel_devotion_reminder_name), NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)

        val b = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(true)
            .setContentText(DevotionReminder.getNotificationText())
            .setContentTitle(context.getString(R.string.dr_notification_title))
            .setContentIntent(pi)
            .setColor(ResourcesCompat.getColor(context.resources, R.color.accent, context.theme))
            .setSmallIcon(R.drawable.ic_stat_reminder)

        val reminder_sound = Preferences.getString(DevotionReminder.REMINDER_SOUND)
        val reminder_vibrate = Preferences.getBoolean(DevotionReminder.REMINDER_VIBRATE, false)

        when {
            reminder_sound == null -> {
                b.setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            }
            reminder_sound.isEmpty() -> { // None/silent
                // Do nothing
            }
            else -> {
                b.setSound(Uri.parse(reminder_sound))
            }
        }

        if (reminder_vibrate) {
            val pattern = longArrayOf(500, 500, 500, 500, 500, 500, 500, 500, 500)
            b.setVibrate(pattern)
        }

        nm.notify(R.id.NOTIF_reminder, b.build())
    }
}
