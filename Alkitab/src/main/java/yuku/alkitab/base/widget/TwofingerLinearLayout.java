package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import yuku.alkitab.debug.BuildConfig;

public class TwofingerLinearLayout extends LinearLayout {
	public static final String TAG = TwofingerLinearLayout.class.getSimpleName();

	State state = State.none;
	Mode mode = null;
	Listener listener;

	// position of one finger (for swiping left and right)
	PointF onefingerStart = new PointF();

	// distance when twofinger starts
	float startDist;
	// distance when twofinger enters scale mode
	float startScaleDist;

	// average position when twofinger starts
	PointF startAvg = new PointF();

	// minimum distance to be considered swipe
	float threshold_swipe;

	// minimum distance to be considered drag
	float threshold_twofinger_drag;

	// minimum distance change to be considered scale
	float threshold_twofinger_scale;

	// if not enabled, one finger gestures, i.e. swipe, will not be captured.
	// by default it's true
	boolean onefingerEnabled = true;

	// if not enabled, two finger gestures will not be captured.
	// by default it's true
	boolean twofingerEnabled = true;

	public TwofingerLinearLayout(Context context) {
		super(context);
		init();
	}

	public TwofingerLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		final float density = getResources().getDisplayMetrics().density;
		threshold_swipe = 48.f * density;
		threshold_twofinger_drag = 48.f * density;
		threshold_twofinger_scale = 72.f * density;
	}

	public void setOnefingerEnabled(final boolean onefingerEnabled) {
		this.onefingerEnabled = onefingerEnabled;
	}

	public void setTwofingerEnabled(final boolean twofingerEnabled) {
		this.twofingerEnabled = twofingerEnabled;
	}

	public void setListener(final Listener listener) {
		this.listener = listener;
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		final int action = event.getActionMasked();

		final int pointerCount = event.getPointerCount();
		if (BuildConfig.DEBUG) Log.d(TAG, "Touch (((" + actionToString(action) + " pointer_count=" + pointerCount + "))) " + state);

		float x1 = event.getX(0);
		float y1 = event.getY(0);
		float x2 = 0;
		float y2 = 0;

		if (pointerCount >= 2) {
			x2 = event.getX(1);
			y2 = event.getY(1);

			if (BuildConfig.DEBUG) Log.d(TAG, String.format("--- " + pointerCount + " pointer: (%f,%f) (%f,%f)", x1, y1, x2, y2));
		}

		if (state == State.onefinger_left) {
			listener.onOnefingerLeft();
			state = State.none;
			mode = null;
			return true;
		} else if (state == State.onefinger_right) {
			listener.onOnefingerRight();
			state = State.none;
			mode = null;
			return true;
		} else if (state == State.twofinger_start) {
			if (pointerCount >= 2) {
				startDist = dist(x1 - x2, y1 - y2);
				startAvg.x = 0.5f * (x1 + x2);
				startAvg.y = 0.5f * (y1 + y2);

				if (BuildConfig.DEBUG) Log.d(TAG, "### Start dist=" + startDist + " avg=" + startAvg);

				listener.onTwofingerStart();
				state = State.twofinger_performing;
			}
			return true;
		} else if (state == State.twofinger_performing) {
			if (pointerCount >= 2) {
				float nowDist = dist(x1 - x2, y1 - y2);

				float nowAvgX = 0.5f * (x1 + x2);
				float nowAvgY = 0.5f * (y1 + y2);
				float dx = nowAvgX - startAvg.x;
				float dy = nowAvgY - startAvg.y;

				if (BuildConfig.DEBUG) Log.d(TAG, ">>>>>> drag=(" + dx + "," + dy + ")");

				// start condition
				if (mode == null) {
					float scale = nowDist / startDist;
					float distChange = Math.abs(nowDist - startDist);

					if (BuildConfig.DEBUG) Log.d(TAG, ">>>>>> scale=" + scale);

					// Scale mode is started when scale differs by 10~15% or more
					// and distance between two fingers changes by a certain threshold
					if ((scale < 0.9f || scale >= 1.15f) && (distChange > threshold_twofinger_scale)) {
						mode = Mode.scale;
						startScaleDist = nowDist;
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
					if (BuildConfig.DEBUG) Log.d(TAG, " RESULT: " + mode);

					if (mode == Mode.scale) {
						listener.onTwofingerScale(nowDist / startScaleDist);
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

	float dist(float dx, float dy) {
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		final int action = event.getActionMasked();
		final int pointerCount = event.getPointerCount();

		if (BuildConfig.DEBUG) Log.d(TAG, "Intercept (((" + actionToString(action) + " pointer_count=" + pointerCount + ")))" + state);

		// one finger for swipe left/right
		if (pointerCount == 1) {
			if (action == MotionEvent.ACTION_DOWN) {
				onefingerStart.x = event.getX();
				onefingerStart.y = event.getY();
			} else if (action == MotionEvent.ACTION_MOVE) {
				if (onefingerStart.x == Float.MIN_VALUE) {
					// invalidated
				} else {
					float dx = event.getX() - onefingerStart.x;
					float dy = event.getY() - onefingerStart.y;
					float ady = Math.abs(dy);

					if (onefingerEnabled && dx > threshold_swipe && ady < 0.5f * threshold_swipe) {
						// swipe to right
						state = State.onefinger_right;
						return true;
					} else if (onefingerEnabled && dx < -threshold_swipe && ady < 0.5f * threshold_swipe) {
						// swipe to left
						state = State.onefinger_left;
						return true;
					} else if (ady > threshold_swipe) {
						// invalidate
						onefingerStart.x = Float.MIN_VALUE;
					}
				}
			}
		} else if (pointerCount == 2 && twofingerEnabled) {
			if (action == MotionEvent.ACTION_POINTER_DOWN) {
				state = State.twofinger_start;
				return true;
			}
		}

		return false;
	}

	enum State {
		none,
		onefinger_left,
		onefinger_right,
		twofinger_start,
		twofinger_performing,
	}

	public enum Mode {
		scale,
		drag_x,
		drag_y,
	}

	public interface Listener {
		void onOnefingerLeft();
		void onOnefingerRight();
		void onTwofingerStart();
		void onTwofingerScale(float scale);
		void onTwofingerDragX(float dx);
		void onTwofingerDragY(float dy);
		void onTwofingerEnd(Mode mode);
	}

	public abstract static class OnefingerListener implements Listener {
		@Override
		public void onTwofingerStart() {}

		@Override
		public void onTwofingerScale(final float scale) {}

		@Override
		public void onTwofingerDragX(final float dx) {}

		@Override
		public void onTwofingerDragY(final float dy) {}

		@Override
		public void onTwofingerEnd(final Mode mode) {}
	}

	// From API 19
	/**
	 * Returns a string that represents the symbolic name of the specified unmasked action
	 * such as "ACTION_DOWN", "ACTION_POINTER_DOWN(3)" or an equivalent numeric constant
	 * such as "35" if unknown.
	 *
	 * @param action The unmasked action.
	 * @return The symbolic name of the specified action.
	 * @see android.view.MotionEvent#getAction()
	 */
	public static String actionToString(int action) {
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				return "ACTION_DOWN";
			case MotionEvent.ACTION_UP:
				return "ACTION_UP";
			case MotionEvent.ACTION_CANCEL:
				return "ACTION_CANCEL";
			case MotionEvent.ACTION_OUTSIDE:
				return "ACTION_OUTSIDE";
			case MotionEvent.ACTION_MOVE:
				return "ACTION_MOVE";
			case MotionEvent.ACTION_HOVER_MOVE:
				return "ACTION_HOVER_MOVE";
			case MotionEvent.ACTION_SCROLL:
				return "ACTION_SCROLL";
			case MotionEvent.ACTION_HOVER_ENTER:
				return "ACTION_HOVER_ENTER";
			case MotionEvent.ACTION_HOVER_EXIT:
				return "ACTION_HOVER_EXIT";
		}
		int index = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
		switch (action & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_POINTER_DOWN:
				return "ACTION_POINTER_DOWN(" + index + ")";
			case MotionEvent.ACTION_POINTER_UP:
				return "ACTION_POINTER_UP(" + index + ")";
			default:
				return Integer.toString(action);
		}
	}
}