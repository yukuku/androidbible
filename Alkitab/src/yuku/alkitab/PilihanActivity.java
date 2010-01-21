package yuku.alkitab;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PilihanActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.pilihan);
	}
}
