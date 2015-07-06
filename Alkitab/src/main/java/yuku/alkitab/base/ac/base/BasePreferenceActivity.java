package yuku.alkitab.base.ac.base;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.MenuItem;
import com.example.android.supportv7.app.AppCompatPreferenceActivity;
import yuku.alkitab.base.util.ChangeLanguageHelper;

public abstract class BasePreferenceActivity extends AppCompatPreferenceActivity {
	static final String TAG = BasePreferenceActivity.class.getSimpleName();

	private int lastKnownLocaleSerialNumber;

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		lastKnownLocaleSerialNumber = ChangeLanguageHelper.getLocaleSerialCounter();
	}

	@Override
	protected void onStart() {
		super.onStart();

		final int currentLocaleSerialNumber = ChangeLanguageHelper.getLocaleSerialCounter();
		if (lastKnownLocaleSerialNumber != currentLocaleSerialNumber) {
			Log.d(TAG, "Restarting activity " + getClass().getName() + " because of locale change " + lastKnownLocaleSerialNumber + " -> " + currentLocaleSerialNumber);
			lastKnownLocaleSerialNumber = currentLocaleSerialNumber;
			// restart activity
			final Intent originalIntent = getIntent();
			finish();
			startActivity(originalIntent);
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
