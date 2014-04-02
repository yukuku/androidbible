package yuku.alkitab.reminder.ac;

import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import yuku.afw.storage.Preferences;
import yuku.alkitab.reminder.R;
import yuku.alkitab.reminder.util.DevotionReminder;
import yuku.alkitab.reminder.widget.ReminderTimePreference;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class DevotionReminderActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener{
	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.devotion_reminder_settings);

		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

		updateReminderTypeEnabledness();

		updateDisplayedValue();
	}

	@SuppressWarnings("deprecation") void updateReminderTypeEnabledness() {
		Preference reminder_time = findPreference(DevotionReminder.REMINDER_TIME);
		Preference reminder_sound = findPreference(DevotionReminder.REMINDER_SOUND);
		Preference reminder_vibrate = findPreference(DevotionReminder.REMINDER_VIBRATE);
		Preference reminder_text = findPreference(DevotionReminder.REMINDER_TEXT);

		boolean disable = reminder_time.shouldDisableDependents();

		reminder_sound.setEnabled(!disable);
		reminder_vibrate.setEnabled(!disable);
		reminder_text.setEnabled(!disable);
	}

	@SuppressWarnings("deprecation") @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		updateReminderTypeEnabledness();
		if (TextUtils.isEmpty(Preferences.getString(DevotionReminder.REMINDER_TEXT))) {
			Preferences.setString(DevotionReminder.REMINDER_TEXT, DevotionReminder.getNotificationText());
		}
		DevotionReminder.scheduleAlarm(this);
		updateDisplayedValue();
	}

	@SuppressWarnings("deprecation") void updateDisplayedValue() {
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

		RingtonePreference soundPreference = (RingtonePreference) findPreference(DevotionReminder.REMINDER_SOUND);
		String soundName;

		if (reminder_sound_uri == null) { // None/silent
			soundName = getString(R.string.dr_off);
		} else {
			final Ringtone ringtone = RingtoneManager.getRingtone(this, reminder_sound_uri);
			if (ringtone != null) { // on some devices this happens!
				soundName = ringtone.getTitle(this);
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
			timePreference.setSummary(DateFormat.getTimeFormat(this).format(time.getTime()));
		}

		//text
		final EditTextPreference textPreference = (EditTextPreference) findPreference(DevotionReminder.REMINDER_TEXT);
		textPreference.setSummary(DevotionReminder.getNotificationText());
		textPreference.setText(DevotionReminder.getNotificationText());
	}

}