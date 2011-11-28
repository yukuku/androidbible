package yuku.alkitab.base.ac;

import android.os.*;
import android.preference.*;
import android.view.*;

import yuku.alkitab.*;
import yuku.alkitab.base.ac.base.*;

public class PengaturanActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.pengaturan);
		setTitle(R.string.pengaturan_alkitab);
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			BaseActivity.backToRootActivity(this);
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
}
