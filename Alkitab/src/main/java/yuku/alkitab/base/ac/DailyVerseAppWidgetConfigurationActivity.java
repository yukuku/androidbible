package yuku.alkitab.base.ac;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.SeekBar;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.br.DailyVerseAppWidgetReceiver;
import yuku.alkitab.base.model.MVersion;
import yuku.alkitab.debug.R;

import java.util.List;

public class DailyVerseAppWidgetConfigurationActivity extends Activity {
	private static final String TAG = DailyVerseAppWidgetConfigurationActivity.class.getSimpleName();
	private VersionAdapter adapter;

	int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
	int selectedVersionPosition = -1;
	CheckBox cDarkText;
	SeekBar sbTextSize;
	TextView tTextSize;
	private CheckBox cTransparentBackground;

	final BroadcastReceiver br = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			if (VersionsActivity.VersionListFragment.ACTION_RELOAD.equals(intent.getAction())) {
				if (adapter != null) adapter.reload();
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setResult(RESULT_CANCELED);
		setContentView(R.layout.activity_daily_verse_configuration);

		final ListView lsVersionsAppWidget = V.get(this, R.id.lsVersionsAppWidget);
		final Button bOk = V.get(this, R.id.bOk);
		final Button bCancel = V.get(this, R.id.bCancel);
		cTransparentBackground = V.get(this, R.id.cTransparentBackground);
		cDarkText = V.get(this, R.id.cDarkText);
		sbTextSize = V.get(this, R.id.sbTextSize);
		tTextSize = V.get(this, R.id.tTextSize);

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
			return;
		}

		String key = "app_widget_" + mAppWidgetId + "_click";
		Preferences.setInt(key, 0);

		adapter = new VersionAdapter();
		adapter.reload();
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

		sbTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
				sbTextSize_progressChanged(progress);
			}

			@Override
			public void onStartTrackingTouch(final SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(final SeekBar seekBar) {}
		});
		sbTextSize_progressChanged(sbTextSize.getProgress());

		App.getLbm().registerReceiver(br, new IntentFilter(VersionsActivity.VersionListFragment.ACTION_RELOAD));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		App.getLbm().unregisterReceiver(br);
	}

	void sbTextSize_progressChanged(final int progress) {
		final float textSize = progressToActualTextSize(progress);
		tTextSize.setText("" + (int) textSize);
	}

	float progressToActualTextSize(final int progress) {
		return progress + 8.f;
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
				Preferences.setFloat("app_widget_" + mAppWidgetId + "_option_text_size", progressToActualTextSize(sbTextSize.getProgress()));
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
		private Pair<List<String>,List<MVersion>> versions;

		void reload() {
			versions = S.getAvailableVersions();
			notifyDataSetChanged();
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