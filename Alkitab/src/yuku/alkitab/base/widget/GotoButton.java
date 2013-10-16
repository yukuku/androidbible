package yuku.alkitab.base.widget;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Button;

public class GotoButton extends Button {
	public static final String TAG = GotoButton.class.getSimpleName();

	public interface FloaterDragListener {
		void onFloaterDragStart(float screenX, float screenY);
		void onFloaterDragMove(float screenX, float screenY);
		void onFloaterDragComplete(float screenX, float screenY);
	}

	int[] screenLocation = {0, 0};
	boolean inFloaterDrag;
	FloaterDragListener floaterDragListener;

	public GotoButton(final Context context) {
		super(context);
		init();
	}

	public GotoButton(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public GotoButton(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init() {
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		final int action = MotionEventCompat.getActionMasked(event);

		float x = event.getX();
		float y = event.getY();

		getLocationOnScreen(screenLocation);
		float screenX = x + screenLocation[0];
		float screenY = y + screenLocation[1];

		if (!inFloaterDrag) {
			if (action == MotionEvent.ACTION_MOVE) {
				if (x < 0 || y < 0 || x > getWidth() || y > getHeight()) {
					cancelLongPress();
					inFloaterDrag = true;
					floaterDragListener.onFloaterDragStart(screenX, screenY);
				}
			}
		}

		// do not use "else"!
		if (inFloaterDrag) {
			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				inFloaterDrag = false;
				floaterDragListener.onFloaterDragComplete(screenX, screenY);
			} else {
				floaterDragListener.onFloaterDragMove(screenX, screenY);
			}
		}

		return super.onTouchEvent(event);
	}

	public void setFloaterDragListener(final FloaterDragListener floaterDragListener) {
		this.floaterDragListener = floaterDragListener;
	}
}
