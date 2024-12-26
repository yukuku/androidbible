package yuku.alkitab.base.ac.base;

import android.view.KeyEvent;
import yuku.alkitab.base.widget.LeftDrawer;

public abstract class BaseLeftDrawerActivity extends BaseActivity {
	@Override
	public boolean onKeyUp(final int keyCode, final KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			openOrCloseLeftDrawer();
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	private void openOrCloseLeftDrawer() {
		getLeftDrawer().toggleDrawer();
	}

	protected abstract LeftDrawer getLeftDrawer();
}
