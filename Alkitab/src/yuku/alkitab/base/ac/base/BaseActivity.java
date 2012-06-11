package yuku.alkitab.base.ac.base;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.view.MenuItem;

import yuku.alkitab.base.IsiActivity;

public abstract class BaseActivity extends FragmentActivity {
	public static final String TAG = BaseActivity.class.getSimpleName();
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
            backToRootActivity(this);
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}

	public static void backToRootActivity(Context context) {
		Intent intent = new Intent(context, IsiActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(intent);
	}
}
