package yuku.alkitab.reminder.br

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.DevotionActivity
import yuku.alkitab.debug.R
import yuku.alkitab.reminder.util.DevotionReminder

private const val NOTIFICATION_CHANNEL_ID = "devotion_reminder"

class DevotionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pi = PendingIntent.getActivity(context, 0, DevotionActivity.createIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.notification_channel_devotion_reminder_name), NotificationManager.IMPORTANCE_DEFAULT)
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }

        val b = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(true)
            .setContentText(DevotionReminder.getNotificationText())
            .setContentTitle(context.getString(R.string.dr_notification_title))
            .setContentIntent(pi)
            .setColor(ResourcesCompat.getColor(context.resources, R.color.accent, context.theme))
            .setSmallIcon(R.drawable.ic_stat_reminder)
        val reminder_sound = Preferences.getString(DevotionReminder.REMINDER_SOUND)
        val reminder_vibrate = Preferences.getBoolean(DevotionReminder.REMINDER_VIBRATE, false)
        if (reminder_sound == null) {
            b.setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
        } else if (reminder_sound.isEmpty()) { // None/silent
            // Do nothing
        } else {
            b.setSound(Uri.parse(reminder_sound))
        }
        if (reminder_vibrate) {
            val pattern = longArrayOf(500, 500, 500, 500, 500, 500, 500, 500, 500)
            b.setVibrate(pattern)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(R.id.NOTIF_reminder, b.build())
    }

    companion object {
    }
}