package yuku.alkitab.base.ac.base;

import android.content.Context;
import android.content.Intent;

import yuku.alkitab.base.IsiActivity;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

public abstract class BaseActivity extends SherlockFragmentActivity {
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
