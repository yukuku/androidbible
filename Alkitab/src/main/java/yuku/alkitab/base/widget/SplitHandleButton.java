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
		void onHandleDragMoveX(float dxSinceLast, float dxSinceStart);
		void onHandleDragMoveY(float dySinceLast, float dySinceStart);
		void onHandleDragStop();
	}

	public enum Orientation {
		vertical, // top bottom
		horizontal, // left right
	}

	protected Orientation orientation = Orientation.vertical; // should not ever be null

	SplitHandleButtonListener listener;
	
	float down;
	float move;
	int[] loc = {0, 0};
	
	public SplitHandleButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public void setListener(SplitHandleButtonListener listener) {
		this.listener = listener;
	}

	public Orientation getOrientation() {
		return orientation;
	}

	public void setOrientation(final Orientation orientation) {
		this.orientation = orientation;
	}

	@Override public boolean onTouchEvent(MotionEvent event) {
		getLocationOnScreen(loc);

		final int action = event.getActionMasked();
		switch (action) {
			case ACTION_DOWN:
				if (orientation == Orientation.vertical) {
					down = move = event.getY() + loc[1];
				} else {
					down = move = event.getX() + loc[0];
				}
				if (listener != null) listener.onHandleDragStart();
				setPressed(true);
				return true;
			case ACTION_MOVE:
				if (orientation == Orientation.vertical) {
					final float y = event.getY() + loc[1];
					if (listener != null) listener.onHandleDragMoveY(y - move, y - down);
					move = y;
				} else {
					final float x = event.getX() + loc[0];
					if (listener != null) listener.onHandleDragMoveX(x - move, x - down);
					move = x;
				}
				return true;
			case ACTION_CANCEL:
			case ACTION_UP:
				if (listener != null) listener.onHandleDragStop();
				setPressed(false);
				return true;
		}
		return false;
	}
}
