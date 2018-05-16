package yuku.alkitab.reminder.ac;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.DateFormat;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;
import yuku.alkitab.reminder.util.DevotionReminder;
import yuku.alkitab.reminder.widget.ReminderTimePreference;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class DevotionReminderActivity extends BaseActivity {
	public static final int REQCODE_ringtone = 1;

	public static Intent createIntent() {
		return new Intent(App.context, DevotionReminderActivity.class);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_devotion_reminder);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);
	}

	public static class DevotionReminderFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			Preferences.registerObserver(this);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();

			Preferences.unregisterObserver(this);
		}

		@Override
		public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
			addPreferencesFromResource(R.xml.devotion_reminder_settings);

			updateReminderTypeEnabledness();

			updateDisplayedValue();
		}

		void updateReminderTypeEnabledness() {
			Preference reminder_time = findPreference(DevotionReminder.REMINDER_TIME);
			Preference reminder_sound = findPreference(DevotionReminder.REMINDER_SOUND);
			Preference reminder_vibrate = findPreference(DevotionReminder.REMINDER_VIBRATE);
			Preference reminder_text = findPreference(DevotionReminder.REMINDER_TEXT);

			boolean disable = reminder_time.shouldDisableDependents();

			reminder_sound.setEnabled(!disable);
			reminder_vibrate.setEnabled(!disable);
			reminder_text.setEnabled(!disable);
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			updateReminderTypeEnabledness();
			if (TextUtils.isEmpty(Preferences.getString(DevotionReminder.REMINDER_TEXT))) {
				Preferences.setString(DevotionReminder.REMINDER_TEXT, DevotionReminder.getNotificationText());
			}
			DevotionReminder.scheduleAlarm();
			updateDisplayedValue();
		}

		@Override
		public boolean onPreferenceTreeClick(final Preference preference) {
			if (DevotionReminder.REMINDER_SOUND.equals(preference.getKey())) {
				final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);

				final String existingValue = Preferences.getString(DevotionReminder.REMINDER_SOUND);
				if (existingValue != null) {
					if (existingValue.length() == 0) { // "Silent"
						intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
					} else {
						intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingValue));
					}
				} else {
					// No ringtone has been selected, set to the default
					intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
				}

				startActivityForResult(intent, REQCODE_ringtone);

				return true;
			}

			return super.onPreferenceTreeClick(preference);
		}

		void updateDisplayedValue() {
			//sound
			final String reminder_sound = Preferences.getString(DevotionReminder.REMINDER_SOUND);
			Uri reminder_sound_uri;

			if (reminder_sound == null) {
				reminder_sound_uri = Settings.System.DEFAULT_NOTIFICATION_URI;
			} else if (reminder_sound.length() != 0) {
				reminder_sound_uri = Uri.parse(reminder_sound);
			} else { // None/silent
				reminder_sound_uri = null;
			}

			final Preference soundPreference = findPreference(DevotionReminder.REMINDER_SOUND);
			String soundName;

			if (reminder_sound_uri == null) { // None/silent
				soundName = getString(R.string.dr_off);
			} else {
				final Ringtone ringtone = RingtoneManager.getRingtone(getActivity(), reminder_sound_uri);
				if (ringtone != null) { // on some devices this happens!
					soundName = ringtone.getTitle(getActivity());
				} else {
					soundName = getString(R.string.dr_default_sound);
				}
			}

			soundPreference.setSummary(soundName);

			//time
			final ReminderTimePreference timePreference = (ReminderTimePreference) findPreference(DevotionReminder.REMINDER_TIME);
			final String currentValue = Preferences.getString(DevotionReminder.REMINDER_TIME, null);

			if (currentValue == null) {
				timePreference.setSummary(R.string.dr_off);
			} else {
				Calendar time = GregorianCalendar.getInstance();
				time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(currentValue.substring(0, 2)));
				time.set(Calendar.MINUTE, Integer.parseInt(currentValue.substring(2, 4)));
				timePreference.setSummary(DateFormat.getTimeFormat(getActivity()).format(time.getTime()));
			}

			//text
			final EditTextPreference textPreference = (EditTextPreference) findPreference(DevotionReminder.REMINDER_TEXT);
			textPreference.setSummary(DevotionReminder.getNotificationText());
			textPreference.setText(DevotionReminder.getNotificationText());
		}

		@Override
		public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
			if (requestCode == REQCODE_ringtone && resultCode == RESULT_OK && data != null) {
				final Uri value = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
				if (value != null) {
					Preferences.setString(DevotionReminder.REMINDER_SOUND, value.toString());
				} else {
					Preferences.setString(DevotionReminder.REMINDER_SOUND, "");
				}
				return;
			}

			super.onActivityResult(requestCode, resultCode, data);
		}
	}
}