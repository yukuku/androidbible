package yuku.alkitab.base.br

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.app.JobIntentService
import java.util.Calendar
import java.util.GregorianCalendar
import yuku.alkitab.base.appwidget.DailyVerseAppWidgetService
import yuku.alkitab.base.appwidget.DailyVerseData
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitabintegration.display.Launcher

private const val TAG = "UpdateService"

/**
 * This is here because we can't update this component's name without breaking older version users.
 */
class DailyVerseAppWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, UpdateService::class.java)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        UpdateService.enqueueWork(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            DailyVerseData.saveSavedState(appWidgetId, null)
        }
    }

    class UpdateService : JobIntentService() {
        override fun onHandleWork(intent: Intent) {
            AppLog.d(TAG, "@@onHandleWork")
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: run {
                AppLog.e(TAG, "appWidgetIds is null")
                return
            }

            for (appWidgetId in appWidgetIds) {
                buildUpdate(this, appWidgetId)
            }

            val mgr = AppWidgetManager.getInstance(this)
            val allWidgetIds = mgr.getAppWidgetIds(ComponentName(this, DailyVerseAppWidgetReceiver::class.java)) ?: intArrayOf()
            setAlarm(this, allWidgetIds)
        }

        companion object {
            /**
             * Unique job ID for this service.
             */
            private const val JOB_ID = 16304444

            /**
             * Convenience method for enqueuing work in to this service.
             */
            fun enqueueWork(context: Context, work: Intent) {
                enqueueWork(context, UpdateService::class.java, JOB_ID, work)
            }
        }
    }

    class ClickReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val bundle = intent.extras ?: return
            val appWidgetAction = bundle.getString("app_widget_action") ?: return

            when (appWidgetAction) {
                "update_widget" -> {
                    val appWidgetIds = bundle.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    if (appWidgetIds != null) {
                        val direction = when (intent.getIntExtra("app_widget_button", 0)) {
                            1 -> -1
                            2 -> 1
                            else -> throw RuntimeException("No such widget button")
                        }

                        for (appWidgetId in appWidgetIds) {
                            val savedState = DailyVerseData.loadSavedState(appWidgetId)
                            savedState.click += direction // manually adjust

                            // pass in through this method to verify that verses exist
                            DailyVerseData.getAris(appWidgetId, savedState, DailyVerseData.getVersion(savedState.versionId), direction)
                            DailyVerseData.saveSavedState(appWidgetId, savedState)
                        }
                    }

                    context.sendBroadcast(Intent(context, DailyVerseAppWidgetReceiver::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    })
                }
                "open_app" -> {
                    context.startActivity(Launcher.getBaseViewIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                "open_verse" -> {
                    val viewVerseIntent = Launcher.getBaseViewIntent()
                        .putExtra("ari", bundle.getInt("ari"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(viewVerseIntent)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun buildUpdate(context: Context, appWidgetId: Int) {
            // get saved state
            val savedState = DailyVerseData.loadSavedState(appWidgetId)

            // prepare remote views
            val rv = RemoteViews(context.packageName, R.layout.appwidget_daily_verse_container)
            if (savedState.transparentBackground) {
                rv.setInt(R.id.background, "setAlpha", savedState.backgroundAlpha)
            }

            val svcIntent = Intent(context, DailyVerseAppWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(DailyVerseAppWidgetService.EXTRA_direction, appWidgetId)
                data = Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME))
            }
            rv.setRemoteAdapter(R.id.lsItems, svcIntent)

            // Set general intent
            val generalIntent = Intent(context, ClickReceiver::class.java)
            rv.setPendingIntentTemplate(
                R.id.lsItems,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 2,
                    generalIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Lastly, update and notify listview as well
            val mgr = AppWidgetManager.getInstance(context)
            mgr.updateAppWidget(appWidgetId, rv)
            mgr.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lsItems)
        }

        @JvmStatic
        fun setAlarm(context: Context, ids: IntArray) {
            val intent = Intent(context, DailyVerseAppWidgetReceiver::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)

            val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val c = GregorianCalendar.getInstance()
            if (BuildConfig.DEBUG) {
                // 60 seconds for debugging
                val interval = 60 * 1000
                mgr.setRepeating(AlarmManager.RTC, c.timeInMillis + interval, interval.toLong(), pi)
            } else {
                // normal: daily
                c[Calendar.HOUR_OF_DAY] = 0
                c[Calendar.MINUTE] = 0
                c[Calendar.SECOND] = 0
                c.add(Calendar.DAY_OF_YEAR, 1)
                mgr.setRepeating(AlarmManager.RTC, c.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
            }
        }
    }
}
