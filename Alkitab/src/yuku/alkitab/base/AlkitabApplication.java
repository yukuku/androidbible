package yuku.alkitab.base;

import yuku.alkitab.R;
import yuku.alkitab.base.storage.Preferences;
import android.app.Application;
import android.preference.PreferenceManager;

public class AlkitabApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		
		S.setAppContext(getApplicationContext());
		Preferences.setAppContext(getApplicationContext());
		
		PreferenceManager.setDefaultValues(this, R.xml.pengaturan, false);
	}
}
