package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.LinearLayout;

public class TwofingerLinearLayout extends LinearLayout {
	public static final String TAG = TwofingerLinearLayout.class.getSimpleName();

	State state = State.none;
	Mode mode = null;
	Listener listener;

	// distance when twofinger starts
	float startDist;
	// average position when twofinger starts
	PointF startAvg = new PointF();
	float threshold_twofinger_drag;

	public TwofingerLinearLayout(Context context) {
		super(context);
		init();
	}

	public TwofingerLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		threshold_twofinger_drag = 48.f * getResources().getDisplayMetrics().density;
	}

	public void setListener(final Listener listener) {
		this.listener = listener;
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		final int action = event.getActionMasked();

		final int pointerCount = event.getPointerCount();
		Log.d(TAG, "Touch (((" + MotionEvent.actionToString(action) + " pointer_count=" + pointerCount + "))) " + state);

		float x1 = 0;
		float x2 = 0;
		float y1 = 0;
		float y2 = 0;

		if (pointerCount >= 2) {
			x1 = event.getX(0);
			x2 = event.getX(1);
			y1 = event.getY(0);
			y2 = event.getY(1);

			Log.d(TAG, String.format("--- " + pointerCount + " pointer: (%f,%f) (%f,%f)", x1, y1, x2, y2));
		}

		if (state == State.twofinger_start) {
			if (pointerCount >= 2) {
				startDist = distSquared(x1 - x2, y1 - y2);
				startAvg.x = 0.5f * (x1 + x2);
				startAvg.y = 0.5f * (y1 + y2);

				Log.d(TAG, "### Start dist=" + startDist + " avg=" + startAvg);

				listener.onTwofingerStart();
				state = State.twofinger_performing;
			}
			return true;
		} else if (state == State.twofinger_performing) {
			if (pointerCount >= 2) {
				float nowDist = distSquared(x1 - x2, y1 - y2);
				float scale = nowDist / startDist;

				Log.d(TAG, ">>>>>> scale=" + scale);

				float nowAvgX = 0.5f * (x1 + x2);
				float nowAvgY = 0.5f * (y1 + y2);
				float dx = nowAvgX - startAvg.x;
				float dy = nowAvgY - startAvg.y;

				Log.d(TAG, ">>>>>> drag=(" + dx + "," + dy + ")");

				// start condition
				if (mode == null) {
					if (scale < 0.85f || scale >= 1.15f) {
						mode = Mode.scale;
					}
				}
				if (mode == null) {
					if (dx > threshold_twofinger_drag || dx < -threshold_twofinger_drag) {
						if (Math.abs(dy) < Math.abs(dx) * 0.5f) {
							mode = Mode.drag_x; // drag in x
						}
					}
					if (dy > threshold_twofinger_drag || dy < -threshold_twofinger_drag) {
						if (Math.abs(dx) < Math.abs(dy) * 0.5f) {
							mode = Mode.drag_y; // drag in y
						}
					}
				}

				if (mode != null) {
					Log.d(TAG, " RESULT: " + mode);

					if (mode == Mode.scale) {
						listener.onTwofingerScale(scale);
					} else if (mode == Mode.drag_x) {
						listener.onTwofingerDragX(dx);
					} else if (mode == Mode.drag_y) {
						listener.onTwofingerDragY(dy);
					}
				}

				return true;
			} else {
				listener.onTwofingerEnd(mode);
				state = State.none;
				mode = null;

				return true;
			}
		} else {
			listener.onTwofingerEnd(mode);
			state = State.none;
			mode = null;

			return super.onTouchEvent(event);
		}
	}

	float distSquared(float dx, float dy) {
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		final int action = event.getActionMasked();

		Log.d(TAG, "Intercept (((" + MotionEvent.actionToString(action) + " pointer_count=" + event.getPointerCount() + ")))" + state);

		if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() == 2) {
			state = State.twofinger_start;
			return true;
		}

		return false;
	}

	enum State {
		none,
		twofinger_start,
		twofinger_performing,
	}

	public enum Mode {
		scale,
		drag_x,
		drag_y,
	}

	public interface Listener {
		void onTwofingerStart();
		void onTwofingerScale(float scale);
		void onTwofingerDragX(float dx);
		void onTwofingerDragY(float dy);
		void onTwofingerEnd(Mode mode);
	}
}