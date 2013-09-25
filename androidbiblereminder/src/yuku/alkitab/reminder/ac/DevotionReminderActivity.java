package yuku.alkitab.reminder.ac;

import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.format.DateFormat;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import yuku.alkitab.reminder.R;
import yuku.alkitab.reminder.br.DevotionReminderReceiver;
import yuku.alkitab.reminder.widget.ReminderTimePreference;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class DevotionReminderActivity extends SherlockPreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener{
	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.devotion_reminder_settings);

		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

		updateReminderTypeEnabledness();

		updateSummary();
	}

	@SuppressWarnings("deprecation") void updateReminderTypeEnabledness() {
		Preference reminder_time = findPreference("reminder_time");
		Preference reminder_sound = findPreference("reminder_sound");
		Preference reminder_vibrate = findPreference("reminder_vibrate");

		boolean disable = reminder_time.shouldDisableDependents();

		reminder_sound.setEnabled(!disable);
		reminder_vibrate.setEnabled(!disable);
	}

	@Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		updateReminderTypeEnabledness();
		updateSummary();
		DevotionReminderReceiver.scheduleAlarm(this);
	}

	@SuppressWarnings("deprecation") void updateSummary() {
		SharedPreferences sp = getPreferenceScreen().getSharedPreferences();

		//sound
		String soundName = RingtoneManager.getRingtone(this, Uri.parse(sp.getString("reminder_sound", ""))).getTitle(this);
		RingtonePreference soundPreference = (RingtonePreference) findPreference("reminder_sound");
		soundPreference.setSummary(soundName);

		//time
		final ReminderTimePreference timePreference = (ReminderTimePreference) findPreference("reminder_time");
		final String currentValue = sp.getString("reminder_time", null);

		if (currentValue == null) {
			timePreference.setSummary(this.getString(R.string.dr_off));
		} else {
			Calendar time = GregorianCalendar.getInstance();
			time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(currentValue.substring(0, 2)));
			time.set(Calendar.MINUTE, Integer.parseInt(currentValue.substring(2, 4)));
			timePreference.setSummary(DateFormat.getTimeFormat(this).format(time.getTime()));
		}

	}

}