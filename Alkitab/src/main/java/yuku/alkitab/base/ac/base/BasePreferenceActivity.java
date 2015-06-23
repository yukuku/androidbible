package yuku.alkitab.base.ac.base;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import com.example.android.supportv7.app.AppCompatPreferenceActivity;

public abstract class BasePreferenceActivity extends AppCompatPreferenceActivity {

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
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
