package yuku.alkitab.base.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class TextActionBarSpacer extends View {
	public TextActionBarSpacer(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		reconfigureWeight(getResources().getConfiguration());
	}

	@Override
	protected void onConfigurationChanged(final Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		reconfigureWeight(newConfig);
	}

	private void reconfigureWeight(final Configuration config) {
		final float weight;

		// on tablets, make this weight larger
		if (config.screenWidthDp >= 1080) {
			weight = 2.f;
		} else if (config.screenWidthDp >= 720) {
			weight = 1.5f;
		} else if (config.screenWidthDp >= 540) {
			weight = 1.f;
		} else {
			weight = 0.f;
		}

		final ViewGroup.LayoutParams lp = getLayoutParams();
		if (lp instanceof LinearLayout.LayoutParams) {
			((LinearLayout.LayoutParams) lp).weight = weight;
			setLayoutParams(lp);
		}
	}
}
