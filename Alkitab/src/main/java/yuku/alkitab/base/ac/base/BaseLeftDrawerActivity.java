package yuku.alkitab.base.ac.base;

import android.content.Intent;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import androidx.activity.OnBackPressedCallback;
import yuku.alkitab.base.util.RequestCodes;
import yuku.alkitab.base.widget.LeftDrawer;
import yuku.alkitab.base.widget.TextAppearancePanel;
import yuku.alkitab.debug.R;

public abstract class BaseLeftDrawerActivity extends BaseActivity {
	private TextAppearancePanel appearancePanel;
	private OnBackPressedCallback appearanceBackCallback;

	protected void setupTextAppearancePanel(final FrameLayout overlay, final Runnable applyAppearance) {
		appearanceBackCallback = new OnBackPressedCallback(false) {
			@Override
			public void handleOnBackPressed() {
				hideTextAppearancePanel();
			}
		};
		getOnBackPressedDispatcher().addCallback(this, appearanceBackCallback);
		getLeftDrawer().findViewById(R.id.bDisplay).setOnClickListener(v -> {
			getLeftDrawer().closeDrawer();
			if (appearancePanel != null) {
				hideTextAppearancePanel();
				return;
			}
			appearancePanel = new TextAppearancePanel(this, overlay, new TextAppearancePanel.Listener() {
				@Override
				public void onValueChanged() {
					applyNightModeColors();
					applyAppearance.run();
				}

				@Override
				public void onCloseButtonClick() {
					hideTextAppearancePanel();
				}
			}, RequestCodes.FromActivity.TextAppearanceGetFonts, RequestCodes.FromActivity.TextAppearanceCustomColors);
			appearancePanel.show();
			appearanceBackCallback.setEnabled(true);
		});
	}

	private void hideTextAppearancePanel() {
		if (appearancePanel != null) appearancePanel.hide();
		appearancePanel = null;
		appearanceBackCallback.setEnabled(false);
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (appearancePanel != null) appearancePanel.displayValues();
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (appearancePanel != null) appearancePanel.onActivityResult(requestCode);
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
