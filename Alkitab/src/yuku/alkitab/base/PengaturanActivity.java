package yuku.alkitab.base;

import yuku.alkitab.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PengaturanActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.pengaturan);
		setTitle(R.string.pengaturan_alkitab);
	}
}
