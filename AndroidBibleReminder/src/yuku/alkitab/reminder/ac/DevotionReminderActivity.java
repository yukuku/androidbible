package yuku.alkitab.reminder.ac;

import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;
import android.text.format.DateFormat;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import yuku.afw.storage.Preferences;
import yuku.alkitab.reminder.R;
import yuku.alkitab.reminder.util.DevotionReminder;
import yuku.alkitab.reminder.widget.ReminderTimePreference;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class DevotionReminderActivity extends SherlockPreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener{
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
		SharedPreferences sp = getPreferenceScreen().getSharedPreferences();

		//sound
		String soundName = RingtoneManager.getRingtone(this, Uri.parse(sp.getString(DevotionReminder.REMINDER_SOUND, ""))).getTitle(this);
		RingtonePreference soundPreference = (RingtonePreference) findPreference(DevotionReminder.REMINDER_SOUND);
		soundPreference.setSummary(soundName);

		//time
		final ReminderTimePreference timePreference = (ReminderTimePreference) findPreference(DevotionReminder.REMINDER_TIME);
		final String currentValue = sp.getString(DevotionReminder.REMINDER_TIME, null);

		if (currentValue == null) {
			timePreference.setSummary(this.getString(R.string.dr_off));
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