package yuku.alkitab.base.br;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import java.util.Calendar;
import java.util.GregorianCalendar;
import yuku.alkitab.base.appwidget.DailyVerseAppWidgetService;
import yuku.alkitab.base.appwidget.DailyVerseData;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitabintegration.display.Launcher;

/**
 * This is here because we can't update this component's name without breaking older version users.
 */
public class DailyVerseAppWidgetReceiver extends AppWidgetProvider {
	public static void buildUpdate(final Context context, final int appWidgetId) {
		// get saved state
		final DailyVerseData.SavedState savedState = DailyVerseData.loadSavedState(appWidgetId);

		// prepare remote views
		final RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.appwidget_daily_verse_container);

		if (savedState.transparentBackground) {
			rv.setInt(R.id.background, "setAlpha", savedState.backgroundAlpha);
		}

		final Intent svcIntent = new Intent(context, DailyVerseAppWidgetService.class);
		svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
		svcIntent.putExtra(DailyVerseAppWidgetService.EXTRA_direction, appWidgetId);
		svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));
		rv.setRemoteAdapter(R.id.lsItems, svcIntent);

		//-----Set general intent
		final Intent generalIntent = new Intent(context, ClickReceiver.class);
		rv.setPendingIntentTemplate(R.id.lsItems, PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, generalIntent, PendingIntent.FLAG_CANCEL_CURRENT));

		// Lastly, update and notify listview as well
		final AppWidgetManager mgr = AppWidgetManager.getInstance(context);
		mgr.updateAppWidget(appWidgetId, rv);
		mgr.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lsItems);
	}

	public static void setAlarm(Context context, int[] ids) {
		final Intent intent = new Intent(context, DailyVerseAppWidgetReceiver.class);
		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);

		final PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

		final AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		final Calendar c = GregorianCalendar.getInstance();
		if (BuildConfig.DEBUG) {
			// 15 seconds for debugging
			final int interval = 15 * 1000;
			mgr.setRepeating(AlarmManager.RTC, c.getTimeInMillis() + interval, interval, pi);
		} else {
			// normal: daily
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.add(Calendar.DAY_OF_YEAR, 1);

			mgr.setRepeating(AlarmManager.RTC, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
		}
	}

	@Override
	public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
		final Intent intent = new Intent(context, UpdateService.class);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
		UpdateService.enqueueWork(context, intent);
	}

	@Override
	public void onDeleted(final Context context, final int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
		for (int appWidgetId : appWidgetIds) {
			DailyVerseData.saveSavedState(appWidgetId, null);
		}
	}

	public static class UpdateService extends JobIntentService {
		static final String TAG = UpdateService.class.getSimpleName();

		/**
		 * Unique job ID for this service.
		 */
		static final int JOB_ID = 16304444;

		/**
		 * Convenience method for enqueuing work in to this service.
		 */
		static void enqueueWork(@NonNull Context context, @NonNull Intent work) {
			enqueueWork(context, UpdateService.class, JOB_ID, work);
		}

		@Override
		protected void onHandleWork(@NonNull final Intent intent) {
			AppLog.d(TAG, "@@onHandleWork");

			final int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
			if (appWidgetIds == null) {
				AppLog.e(TAG, "appWidgetIds is null");
				return;
			}

			for (int appWidgetId : appWidgetIds) {
				buildUpdate(this, appWidgetId);
			}

			final AppWidgetManager mgr = AppWidgetManager.getInstance(this);
			final ComponentName componentName = new ComponentName(this, DailyVerseAppWidgetReceiver.class);
			final int[] allWidgetIds = mgr.getAppWidgetIds(componentName);
			setAlarm(this, allWidgetIds);
		}
	}

	public static class ClickReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final Bundle bundle = intent.getExtras();
			if (bundle == null) {
				return;
			}

			final String appWidgetAction = bundle.getString("app_widget_action");
			if (appWidgetAction == null) return;

			switch (appWidgetAction) {
				case "update_widget": {
					final int[] appWidgetIds = bundle.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
					if (appWidgetIds != null) {
						final int buttonRequest = intent.getIntExtra("app_widget_button", 0);
						final int direction;
						if (buttonRequest == 1) {
							direction = -1;
						} else if (buttonRequest == 2) {
							direction = 1;
						} else {
							throw new RuntimeException("No such widget button");
						}

						for (int appWidgetId : appWidgetIds) {
							final DailyVerseData.SavedState savedState = DailyVerseData.loadSavedState(appWidgetId);
							savedState.click += direction; // manually adjust
							// pass in through this method to verify that verses exist
							DailyVerseData.getAris(appWidgetId, savedState, DailyVerseData.getVersion(savedState.versionId), direction);
							DailyVerseData.saveSavedState(appWidgetId, savedState);
						}
					}

					final Intent i = new Intent(context, DailyVerseAppWidgetReceiver.class);
					i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
					i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
					context.sendBroadcast(i);
				}
				break;

				case "open_app": {
					context.startActivity(Launcher.getBaseViewIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				}
				break;

				case "open_verse": {
					final Intent viewVerseIntent = Launcher.getBaseViewIntent()
						.putExtra("ari", bundle.getInt("ari"))
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

					context.startActivity(viewVerseIntent);
				}
				break;
			}
		}
	}
}