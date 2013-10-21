package yuku.alkitab.base.ac;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.ListView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.br.DailyVerseAppWidgetReceiver;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.debug.R;

import java.util.ArrayList;
import java.util.List;

public class DailyVerseAppWidgetConfigurationActivity extends Activity {
	private static final String TAG = DailyVerseAppWidgetConfigurationActivity.class.getSimpleName();
	private VersionAdapter adapter;

	int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
	int selectedVersionPosition = -1;
	CheckBox cDarkText;
	private CheckBox cTransparentBackground;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setResult(RESULT_CANCELED);
		setContentView(R.layout.activity_daily_verse_configuration);

		final ListView lsVersionsAppWidget = V.get(this, R.id.lsVersionsAppWidget);
		final Button bOk = V.get(this, R.id.bOk);
		final Button bCancel = V.get(this, R.id.bCancel);
		cTransparentBackground = V.get(this, R.id.cTransparentBackground);
		cDarkText = V.get(this, R.id.cDarkText);

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

		String key = "app_widget_" + mAppWidgetId + "_click";
		Preferences.setInt(key, 0);

		adapter = new VersionAdapter();
		adapter.load();
		lsVersionsAppWidget.setAdapter(adapter);
		lsVersionsAppWidget.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				selectedVersionPosition = position;
				bOk.setEnabled(true);
				adapter.notifyDataSetChanged();
			}
		});

		bOk.setEnabled(false);
		bOk.setOnClickListener(bOk_click);
		bCancel.setOnClickListener(bCancel_click);

		cTransparentBackground.setOnCheckedChangeListener(cTransparentBackground_checkedChange);
	}

	private View.OnClickListener bOk_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			final DailyVerseAppWidgetConfigurationActivity context = DailyVerseAppWidgetConfigurationActivity.this;
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
			String versionId = adapter.versions.second.get(selectedVersionPosition).getVersionId();

			Preferences.hold();
			try {
				Preferences.setString("app_widget_" + mAppWidgetId + "_version", versionId);
				Preferences.setBoolean("app_widget_" + mAppWidgetId + "_option_transparent_background", cTransparentBackground.isChecked());
				Preferences.setBoolean("app_widget_" + mAppWidgetId + "_option_dark_text", cDarkText.isChecked());
			} finally {
				Preferences.unhold();
			}

			DailyVerseAppWidgetReceiver.buildUpdate(context, appWidgetManager, mAppWidgetId);

			ComponentName provider = new ComponentName(context, DailyVerseAppWidgetReceiver.class);
			int[] ids = AppWidgetManager.getInstance(context).getAppWidgetIds(provider);
			DailyVerseAppWidgetReceiver.setAlarm(DailyVerseAppWidgetConfigurationActivity.this, ids);

			Intent resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
			setResult(RESULT_OK, resultValue);
			finish();
		}
	};

	private View.OnClickListener bCancel_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			finish();
		}
	};

	private CompoundButton.OnCheckedChangeListener cTransparentBackground_checkedChange = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
			cDarkText.setEnabled(isChecked);
			if (!isChecked) {
				cDarkText.setChecked(false);
			}
		}
	};

	class VersionAdapter extends EasyAdapter {
		private Pair<List<String>,List<VersionsActivity.MVersion>> versions;

		void load() {
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

			versions = Pair.create(options, data);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_list_item_single_choice, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			CheckedTextView text1 = V.get(view, android.R.id.text1);
			text1.setText(versions.first.get(position));

			text1.setChecked(position == selectedVersionPosition);
		}

		@Override
		public int getCount() {
			return versions.second.size();
		}
	}
}