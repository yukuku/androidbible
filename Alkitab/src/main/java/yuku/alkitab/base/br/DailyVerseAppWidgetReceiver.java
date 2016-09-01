package yuku.alkitab.base.br;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;
import yuku.afw.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.sv.DailyVerseAppWidgetService;
import yuku.alkitab.base.util.DailyVerseData;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitabintegration.display.Launcher;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class DailyVerseAppWidgetReceiver extends AppWidgetProvider {
	public static final String TAG = DailyVerseAppWidgetReceiver.class.getSimpleName();

	public static class UpdateService extends IntentService {
		public UpdateService() {
			super("Widget updater (temporary)");
		}

		@Override
		protected void onHandleIntent(final Intent intent) {
			final int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
			if (appWidgetIds == null) {
				Log.e(TAG, "appWidgetIds is null");
				return;
			}

			for (int appWidgetId : appWidgetIds) {
				buildUpdate(this, appWidgetId, 1);
			}

			final AppWidgetManager mgr = AppWidgetManager.getInstance(this);
			final ComponentName componentName = new ComponentName(this, DailyVerseAppWidgetReceiver.class);
			final int[] allWidgetIds = mgr.getAppWidgetIds(componentName);
			setAlarm(this, allWidgetIds);
		}
	}

	@Override
	public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
		final Intent intent = new Intent(App.context, UpdateService.class);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
		context.startService(intent);
	}

	@Override
	public void onDeleted(final Context context, final int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
		for (int appWidgetId : appWidgetIds) {
			DailyVerseData.saveSavedState(appWidgetId, null);
		}
	}

	public static void buildUpdate(final Context context, final int appWidgetId, final int direction) {
		// get saved state
		final DailyVerseData.SavedState savedState = DailyVerseData.loadSavedState(appWidgetId);

		// prepare remote views
		final RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.daily_verse_app_widget);

		if (savedState.transparentBackground) {
			rv.setInt(R.id.background, "setAlpha", savedState.backgroundAlpha);
		}

		if (savedState.darkText) {
			rv.setTextColor(R.id.tReference, Color.BLACK);
			rv.setImageViewResource(R.id.bPrev, R.drawable.ic_nav_left_dark);
			rv.setImageViewResource(R.id.bNext, R.drawable.ic_nav_right_dark);
		} else {
			rv.setTextColor(R.id.tReference, Color.WHITE);
		}

		rv.setFloat(R.id.tReference, "setTextSize", savedState.textSize);

		final Intent svcIntent = new Intent(context, DailyVerseAppWidgetService.class);
		svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
		svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));
		rv.setRemoteAdapter(R.id.lsVerse, svcIntent);

		final Version version = DailyVerseData.getVersion(savedState.versionId);
		final int[] aris = DailyVerseData.getAris(appWidgetId, savedState, version, direction);
		if (aris != null) {
			rv.setTextViewText(R.id.tReference, version.referenceWithVerseCount(aris[0], aris.length));

			final Intent viewVerseIntent = Launcher.openAppAtBibleLocation(aris[0]);
			rv.setOnClickPendingIntent(R.id.tReference, PendingIntent.getActivity(context, appWidgetId + 10000, viewVerseIntent, PendingIntent.FLAG_UPDATE_CURRENT));
		} else {
			rv.setTextViewText(R.id.tReference, App.context.getString(R.string.generic_verse_not_available_in_this_version));
		}

		//------Set Intent to update widget
		final int[] appWidgetIds = {appWidgetId};
		//--App logo button
		{
			Intent intent = new Intent(App.context, IsiActivity.class);

			PendingIntent pi = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);
			rv.setOnClickPendingIntent(R.id.imgLogo, pi);
		}

		//--Prev button
		{
			Intent intentPrev = new Intent(context, ClickReceiver.class);
			Bundle bundle = new Bundle();
			bundle.putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
			bundle.putString("app_widget_action", "update_widget");
			bundle.putInt("app_widget_button", 1);
			intentPrev.putExtras(bundle);

			PendingIntent pendingIntentPrev = PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, intentPrev, PendingIntent.FLAG_CANCEL_CURRENT);
			rv.setOnClickPendingIntent(R.id.bPrev, pendingIntentPrev);
		}

		//--Next button
		{
			Intent intentNext = new Intent(context, ClickReceiver.class);
			Bundle bundle = new Bundle();
			bundle.putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
			bundle.putString("app_widget_action", "update_widget");
			bundle.putInt("app_widget_button", 2);
			intentNext.putExtras(bundle);

			PendingIntent pendingIntentNext = PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, intentNext, PendingIntent.FLAG_CANCEL_CURRENT);
			rv.setOnClickPendingIntent(R.id.bNext, pendingIntentNext);
		}
		//------End set Intent to update widget

		//-----Set Intent to open bible
		final Intent viewVerseIntent = Launcher.getBaseViewIntent();
		rv.setPendingIntentTemplate(R.id.lsVerse, PendingIntent.getActivity(context, appWidgetId, viewVerseIntent, PendingIntent.FLAG_CANCEL_CURRENT));

		// Lastly, update and notify listview as well
		final AppWidgetManager mgr = AppWidgetManager.getInstance(context);
		mgr.updateAppWidget(appWidgetId, rv);
		mgr.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lsVerse);
	}

	public static class ClickReceiver extends BroadcastReceiver {
		public static final String TAG = ClickReceiver.class.getSimpleName();

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final Bundle bundle = intent.getExtras();
			if (bundle == null) {
				return;
			}

			final String appWidgetAction = bundle.getString("app_widget_action");
			if (!"update_widget".equals(appWidgetAction)) {
				return;
			}

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
}