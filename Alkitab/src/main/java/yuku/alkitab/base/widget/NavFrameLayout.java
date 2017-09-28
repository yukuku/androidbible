package yuku.alkitab.base.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;

public class NavFrameLayout extends FrameLayout {
	static final String TAG = NavFrameLayout.class.getSimpleName();

	public NavFrameLayout(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, final int heightMeasureSpec) {
		// implements some kind of "maxWidth"

		final int maxGivenWidth = MeasureSpec.getSize(widthMeasureSpec);
		final int maxWidth = (int) (getResources().getDisplayMetrics().density * 250);

		if (maxGivenWidth > maxWidth) {
			widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.getMode(widthMeasureSpec));
			if (BuildConfig.DEBUG) AppLog.d(TAG, "wms overriden to: " + MeasureSpec.toString(widthMeasureSpec));
		}

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
}
