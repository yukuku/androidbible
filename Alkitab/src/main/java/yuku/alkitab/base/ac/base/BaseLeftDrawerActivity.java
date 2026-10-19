package yuku.alkitab.base.ac.base;

import android.view.KeyEvent;
import android.widget.FrameLayout;
import yuku.alkitab.base.widget.LeftDrawer;
import yuku.alkitab.debug.R;

public abstract class BaseLeftDrawerActivity extends BaseTextAppearanceActivity {
	@Override
	protected void setupTextAppearancePanel(final FrameLayout overlay, final Runnable applyAppearance) {
		super.setupTextAppearancePanel(overlay, applyAppearance);
		getLeftDrawer().findViewById(R.id.bDisplay).setOnClickListener(v -> {
			getLeftDrawer().closeDrawer();
			toggleTextAppearancePanel();
		});
	}

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
