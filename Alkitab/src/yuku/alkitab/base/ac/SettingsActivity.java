package yuku.alkitab.base.ac;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.ac.base.BaseActivity;

public class SettingsActivity extends PreferenceActivity {
	interface PreferenceUpdate {
		String withValue(Object value);
	}
	
	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.settings);
		setTitle(R.string.pengaturan_alkitab);
		
		autoUpdateSummary(R.string.pref_ukuranHuruf2_key, new PreferenceUpdate() {
			@Override public String withValue(Object value) {
				float dp = value == null? 17.f: (Float) value;
				return String.format("%.1f dp (%.1f pt)", dp, dp * 0.45f); //$NON-NLS-1$
			}
		});
		
		autoUpdateSummary(R.string.pref_jenisHuruf_key, new PreferenceUpdate() {
			@Override public String withValue(Object value) {
				return value != null? (String) value: "Default"; //$NON-NLS-1$
			}
		});
		
		autoUpdateSummary(R.string.pref_lineSpacingMult_key, new PreferenceUpdate() {
			@Override public String withValue(Object value) {
				float lineSpacingMult = value == null? 1.0f: (Float) value;
				return String.valueOf(lineSpacingMult) + "x"; //$NON-NLS-1$
			}
		});
	}

	private void autoUpdateSummary(int keyResId, final PreferenceUpdate pu) {
		String key = getString(keyResId);
		@SuppressWarnings("deprecation") final Preference pref = findPreference(key);
		if (pref != null) {
			pref.setSummary(pu.withValue(Preferences.get(key)));
			pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override public boolean onPreferenceChange(Preference preference, Object newValue) {
					pref.setSummary(pu.withValue(newValue));
					return true;
				}
			});
		}
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			BaseActivity.backToRootActivity(this);
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
}
