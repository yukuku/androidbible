package yuku.alkitab.base.widget;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public class TouchInterceptFrameLayout extends FrameLayout {
	public static final String TAG = TouchInterceptFrameLayout.class.getSimpleName();
	
	private View.OnTouchListener interceptTouchEventListener = null;
	
	public TouchInterceptFrameLayout(Context context) {
		super(context);
	}
	
	public TouchInterceptFrameLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public TouchInterceptFrameLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	@Override public boolean onInterceptTouchEvent(MotionEvent ev) {
		Log.d(TAG, "intercept action " + MotionEventCompat.getActionMasked(ev));
		
		if (interceptTouchEventListener == null) {
			return super.onInterceptTouchEvent(ev);
		} else {
			return interceptTouchEventListener.onTouch(this, ev);
		}
	}

	public View.OnTouchListener getInterceptTouchEventListener() {
		return interceptTouchEventListener;
	}

	public void setInterceptTouchEventListener(View.OnTouchListener interceptTouchEventListener) {
		this.interceptTouchEventListener = interceptTouchEventListener;
	}
}
