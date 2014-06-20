package yuku.alkitab.base.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Button;

import static android.view.MotionEvent.*;

public class SplitHandleButton extends Button {
	public static final String TAG = SplitHandleButton.class.getSimpleName();

	public interface SplitHandleButtonListener {
		void onHandleDragStart();
		void onHandleDragMove(float dySinceLast, float dySinceStart);
		void onHandleDragStop();
	}
	
	SplitHandleButtonListener listener;
	
	float downY;
	float moveY;
	int[] loc = {0, 0};
	
	public SplitHandleButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public void setListener(SplitHandleButtonListener listener) {
		this.listener = listener;
	}
	
	@Override public boolean onTouchEvent(MotionEvent event) {
		getLocationOnScreen(loc);
		
		int action = event.getAction() & ACTION_MASK;
		if (action == ACTION_DOWN) {
			downY = moveY = event.getY() + loc[1];
			if (listener != null) listener.onHandleDragStart();
			setPressed(true);
			return true;
		} else if (action == ACTION_MOVE) {
			float y = event.getY() + loc[1];
			if (listener != null) listener.onHandleDragMove(y - moveY, y - downY);
			moveY = y;
			return true;
		} else if (action == ACTION_CANCEL || action == ACTION_UP) {
			if (listener != null) listener.onHandleDragStop();
			setPressed(false);
			return true;
		}
		return false;
	}
}
