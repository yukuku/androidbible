package yuku.alkitab.base.ac.base;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

public abstract class BasePreferenceActivity extends PreferenceActivity {
	/**
	 * By default we put the up button on each activity.
	 * And the behavior is to just finish() the current activity.
	 * If we don't want this, e.g. on the root activity, change
	 * super.onCreate(Bundle) to super.onCreate(Bundle, boolean)
	 * on subclasses
	 */
	@Override protected void onCreate(Bundle savedInstanceState) {
		onCreate(savedInstanceState, true);
	}
	
	protected void onCreate(Bundle savedInstanceState, boolean withUpButton) {
		super.onCreate(savedInstanceState);
		
		if (withUpButton) {
			final ActionBar actionBar = getActionBar();
			if (actionBar != null) {
				actionBar.setDisplayHomeAsUpEnabled(true);
			}
		}
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
            return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
}
