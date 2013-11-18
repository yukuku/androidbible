package yuku.alkitab.base.widget;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

public class TouchInterceptLinearLayout extends LinearLayout {
	public static final String TAG = TouchInterceptLinearLayout.class.getSimpleName();
	
	private View.OnTouchListener interceptTouchEventListener = null;
	
	public TouchInterceptLinearLayout(Context context) {
		super(context);
	}
	
	public TouchInterceptLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	@Override public boolean onInterceptTouchEvent(MotionEvent ev) {
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
