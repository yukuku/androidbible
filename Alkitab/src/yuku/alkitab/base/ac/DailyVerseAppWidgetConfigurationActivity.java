package yuku.alkitab.base.ac;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckedTextView;
import android.widget.ListView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.widget.DailyVerseAppWidget;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DailyVerseAppWidgetConfigurationActivity extends Activity {
	private static final String TAG = DailyVerseAppWidgetConfigurationActivity.class.getSimpleName();
	private VersionAdapter adapter;
	int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setResult(RESULT_CANCELED);

		setContentView(R.layout.activity_daily_verse_configuration);

		// Find the widget id from the intent.
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
			mAppWidgetId = extras.getInt(
			AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		}

		// If they gave us an intent without the widget id, just bail.
		if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			finish();
		}

		String key = "app_widget_" + mAppWidgetId;
		Preferences.setInt(key, 0);

		ListView lsVersion = V.get(this, R.id.lsVersionsAppWidget);
		adapter = new VersionAdapter();
		adapter.load();
		lsVersion.setAdapter(adapter);
		lsVersion.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				final DailyVerseAppWidgetConfigurationActivity context = DailyVerseAppWidgetConfigurationActivity.this;
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
				String version = adapter.versions.second.get(position).getVersionId();
				DailyVerseAppWidget.buildUpdate(context, appWidgetManager, mAppWidgetId, version, 0);

				Preferences.setString("app_widget_" + mAppWidgetId + "_version", version);

				setAlarm(mAppWidgetId);

				Intent resultValue = new Intent();
				resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
				setResult(RESULT_OK, resultValue);
				finish();
			}
		});

	}

	private void setAlarm(int appWidgetId) {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);

		Intent intent = new Intent(this, DailyVerseAppWidget.class);
		int[] ids = {appWidgetId};
		Bundle bundle = new Bundle();
		bundle.putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
		bundle.putString("app_widget_action", "update_widget");

		intent.putExtras(bundle);

		PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		AlarmManager mgr = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
		mgr.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
		Log.d(TAG, "Alarm is set now");
	}


	class VersionAdapter extends EasyAdapter {


		private Pair<List<String>,List<VersionsActivity.MVersion>> versions;
		private int selected;

		void load() {
			versions = getAvailableVersions();
			final List<VersionsActivity.MVersion> data = versions.second;

			selected = -1;
			if (S.activeVersionId == null) {
				selected = 0;
			} else {
				for (int i = 0; i < data.size(); i++) {
					VersionsActivity.MVersion mv = data.get(i);
					if (mv.getVersionId().equals(S.activeVersionId)) {
						selected = i;
						break;
					}
				}
			}

		}

		private Pair<List<String>, List<VersionsActivity.MVersion>> getAvailableVersions() {
			// populate with
			// 1. internal
			// 2. presets that have been DOWNLOADED and ACTIVE
			// 3. yeses that are ACTIVE

			AppConfig c = AppConfig.get();
			final List<String> options = new ArrayList<String>(); // sync with below line
			final List<VersionsActivity.MVersion> data = new ArrayList<VersionsActivity.MVersion>();  // sync with above line

			options.add(c.internalLongName); // 1. internal
			data.add(new VersionsActivity.MVersionInternal());

			for (VersionsActivity.MVersionPreset preset: c.presets) { // 2. preset
				if (preset.hasDataFile() && preset.getActive()) {
					options.add(preset.longName);
					data.add(preset);
				}
			}

			// 3. active yeses
			List<VersionsActivity.MVersionYes> yeses = S.getDb().listAllVersions();
			for (VersionsActivity.MVersionYes yes: yeses) {
				if (yes.hasDataFile() && yes.getActive()) {
					options.add(yes.longName);
					data.add(yes);
				}
			}

			return Pair.create(options, data);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_list_item_checked, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			CheckedTextView checkedTextView = V.get(view, android.R.id.text1);
			checkedTextView.setText(versions.first.get(position));
			if (position == selected) {
				checkedTextView.setChecked(true);
			}
		}

		@Override
		public int getCount() {
			return versions.second.size();
		}
	}
}