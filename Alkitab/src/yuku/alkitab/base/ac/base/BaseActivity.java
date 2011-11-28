package yuku.alkitab.base.ac.base;

import android.app.*;
import android.content.*;
import android.view.*;

import yuku.alkitab.base.*;

public class BaseActivity extends Activity {
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
