package yuku.alkitab.base.ac.base;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.google.analytics.tracking.android.EasyTracker;

public abstract class BasePreferenceActivity extends SherlockPreferenceActivity {
	@Override protected void onStart() {
		super.onStart();
	    EasyTracker.getInstance().activityStart(this); 
	}
	
	@Override protected void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}
}
