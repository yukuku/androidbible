package yuku.alkitab;

import android.app.*;
import android.preference.*;

public class AlkitabApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		
		PreferenceManager.setDefaultValues(this, R.xml.pengaturan, false);
	}
}
