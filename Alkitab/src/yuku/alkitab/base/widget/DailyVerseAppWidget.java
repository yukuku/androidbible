package yuku.alkitab.base.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Pair;
import android.widget.RemoteViews;
import yuku.afw.App;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.sv.WidgetService;
import yuku.bintex.BintexReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class DailyVerseAppWidget extends AppWidgetProvider {
	public static final String TAG = DailyVerseAppWidget.class.getSimpleName();

	@Override
	public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
		final int N = appWidgetIds.length;
		for (int i = 0; i < N; i++) {
			int appWidgetId = appWidgetIds[i];
			buildUpdate(context, appWidgetManager, appWidgetId);
		}
		setAlarm(context, appWidgetIds);
	}

	@Override
	public void onReceive(final Context context, final Intent intent) {
		super.onReceive(context, intent);
	}

	@Override
	public void onDeleted(final Context context, final int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
		for (int appWidgetId : appWidgetIds) {
			String keyPrefix = "app_widget_" + appWidgetId;
			Preferences.remove(keyPrefix + "_click");
			Preferences.remove(keyPrefix + "_version");
		}
	}

	@SuppressLint("NewApi")
	public static void buildUpdate(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
		//Make new view based on API Version
		RemoteViews remoteViews;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			remoteViews = getRemoteViewsApi14(context, appWidgetId);
		} else {
			remoteViews = getRemoteViews(context, appWidgetId);
		}

		remoteViews.setTextViewText(R.id.tReference, getReference(getVersion(appWidgetId), getVerse(appWidgetId)));

		//------Set Intent to update widget
		int[] ids = {appWidgetId};
		//--Prev button
		{
			Intent intentPrev = new Intent(context, ClickReceiver.class);
			Bundle bundle = new Bundle();
			bundle.putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
			bundle.putString("app_widget_action", "update_widget");
			bundle.putInt("app_widget_button", 1);
			intentPrev.putExtras(bundle);

			PendingIntent pendingIntentPrev = PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, intentPrev, PendingIntent.FLAG_CANCEL_CURRENT);
			remoteViews.setOnClickPendingIntent(R.id.bPrev, pendingIntentPrev);
		}

		//--Next button
		{
			Intent intentNext = new Intent(context, ClickReceiver.class);
			Bundle bundle = new Bundle();
			bundle.putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
			bundle.putString("app_widget_action", "update_widget");
			bundle.putInt("app_widget_button", 2);
			intentNext.putExtras(bundle);

			PendingIntent pendingIntentNext = PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, intentNext, PendingIntent.FLAG_CANCEL_CURRENT);
			remoteViews.setOnClickPendingIntent(R.id.bNext, pendingIntentNext);
		}
		//------End set Intent to update widget

		//-----Set Intent to open bible
		Intent intentOpenBible = new Intent("yuku.alkitab.action.VIEW");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			PendingIntent pendingIntentOpenBible = PendingIntent.getActivity(context, appWidgetId, intentOpenBible, PendingIntent.FLAG_CANCEL_CURRENT);
			remoteViews.setPendingIntentTemplate(R.id.lsVerse, pendingIntentOpenBible);
		} else {
			intentOpenBible.putExtra("ari", getVerse(appWidgetId)[0]);
			PendingIntent pendingIntentOpenBible = PendingIntent.getActivity(context, appWidgetId, intentOpenBible, PendingIntent.FLAG_CANCEL_CURRENT);

			remoteViews.setOnClickPendingIntent(R.id.tVerse, pendingIntentOpenBible);
		}
		//-----End of Intent to open bible
		appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static RemoteViews getRemoteViewsApi14(final Context context, final int appWidgetId) {Intent svcIntent = new Intent(context, WidgetService.class);
		svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
		svcIntent.putExtra("random", new Random().nextInt());
		svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.app_widget);
		remoteViews.setRemoteAdapter(R.id.lsVerse, svcIntent);
		return remoteViews;
	}

	private static RemoteViews getRemoteViews(final Context context, final int appWidgetId) {
		SpannableStringBuilder verseText = new SpannableStringBuilder();
		Version version = getVersion(appWidgetId);
		int[] aris = getVerse(appWidgetId);
		boolean showVerseNumber = false;
		if (aris.length > 1) {
			showVerseNumber = true;
		}
		for (int ari : aris) {
			verseText.append(getText(version, ari, showVerseNumber));
		}
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.app_widget);
		remoteViews.setTextViewText(R.id.tVerse, verseText);

		return remoteViews;
	}

	public static class ClickReceiver extends BroadcastReceiver {
		public static final String TAG = ClickReceiver.class.getSimpleName();
		@Override
		public void onReceive(final Context context, final Intent intent) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				String appWidgetAction = bundle.getString("app_widget_action");
				if (appWidgetAction != null && appWidgetAction.equals("update_widget")) {
					int[] appWidgetIds = bundle.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
					if (appWidgetIds != null) {
						int buttonRequest = intent.getIntExtra("app_widget_button", 0);
						int add = 0;
						if (buttonRequest == 1) {
							add = -1;
						} else if (buttonRequest == 2) {
							add = 1;
						}

						for (int id : appWidgetIds) {
							String key = "app_widget_" + id + "_click";
							int numOfClick = Preferences.getInt(key, 0) + add;
							Preferences.setInt(key, numOfClick);
						}
					}
					Intent i = new Intent(context, DailyVerseAppWidget.class);
					i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
					i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
					context.sendBroadcast(i);
				}
			}
		}
	}

	public static void setAlarm(Context context, int[] ids) {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.add(Calendar.DAY_OF_YEAR, 1);

		Intent intent = new Intent(context, DailyVerseAppWidget.class);
		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);

		PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

		AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		mgr.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
	}

	public static Version getVersion(int appWidgetId) {
		String version = Preferences.getString("app_widget_" + appWidgetId + "_version", S.activeVersionId);
		if (version == null) {
			return S.activeVersion;
		}

		AppConfig c = AppConfig.get();

		if (version == null) {
			return null;
		}

		if (VersionsActivity.MVersionInternal.getVersionInternalId().equals(version)) {
			return Version.getInternalVersion();
		}

		// coba preset dulu!
		for (VersionsActivity.MVersionPreset preset: c.presets) { // 2. preset
			if (preset.getVersionId().equals(version)) {
				if (preset.hasDataFile()) {
					return preset.getVersion();
				} else {
					return null;
				}
			}
		}

		// masih belum cocok, mari kita cari di daftar yes
		List<VersionsActivity.MVersionYes> yeses = S.getDb().listAllVersions();
		for (VersionsActivity.MVersionYes yes: yeses) {
			if (yes.getVersionId().equals(version)) {
				if (yes.hasDataFile()) {
					return yes.getVersion();
				} else {
					return null;
				}
			}
		}
		return null;
	}

	public static int[] getVerse(int appWidgetId) {
		Pair<List<Integer>, List<Integer>> dailyVerses = listAllDailyVerses();
		int size = dailyVerses.first.size();

		String key = "app_widget_" + appWidgetId + "_click";
		int numOfClick = Preferences.getInt(key, 0);

		Date date = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		long year = calendar.get(Calendar.YEAR);
		long day = calendar.get(Calendar.DAY_OF_YEAR);
		long randomDay = (year - 1900) * 1000 + day;

		long randomNumberSeed = randomDay * 10000000 + appWidgetId * 100000 + numOfClick * 1000;
		Random r = new Random(randomNumberSeed);
		int random = (int) (r.nextDouble() * size);

		int verseCount = dailyVerses.second.get(random);
		int[] verses = new int[verseCount];
		verses[0] = dailyVerses.first.get(random);
		for (int i = 1; i < verseCount; i++) {
			verses[i] = verses[i - 1] + 1;
		}
		return verses;
	}

	private static Pair<List<Integer>, List<Integer>> listAllDailyVerses() {
		List<Integer> aris = new ArrayList<Integer>();
		List<Integer> verseCounts = new ArrayList<Integer>();
		try {
			InputStream is = App.context.getResources().openRawResource(R.raw.daily_verse);
			BintexReader br = new BintexReader(is);
			while (true) {
				int ari = br.readInt();
				if (ari == -1) {
					break;
				}
				aris.add(ari);
				verseCounts.add(br.readUint8());
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		return Pair.create(aris, verseCounts);
	}

	public static SpannableStringBuilder getText(Version version, int ari, boolean showVerseNumber) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		String verseText = U.removeSpecialCodes(version.loadVerseText(ari));
		if (showVerseNumber) {
			String verseNumber = "" + Ari.toVerse(ari);
			sb.append(verseNumber + " " + verseText);
			sb.setSpan(new RelativeSizeSpan(0.7f), 0, verseNumber.length(), 0);
		} else {
			sb.append(verseText);
		}
		return sb;
	}

	public static SpannableStringBuilder getReference(Version version, int[] ari) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		String reference = version.reference(ari[0]);
		sb.append(reference);
		if (ari.length > 1) {
			int lastVerse = Ari.toVerse(ari[0]) + ari.length - 1;
			sb.append("-" + lastVerse);
		}
		return sb;
	}
}