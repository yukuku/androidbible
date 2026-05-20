package yuku.alkitab.base.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.appcompat.widget.AppCompatButton;
import yuku.alkitab.debug.R;

public class GotoButton extends AppCompatButton {
	public interface FloaterDragListener {
		void onFloaterDragStart(float screenX, float screenY);

		void onFloaterDragMove(float screenX, float screenY);

		void onFloaterDragComplete(float screenX, float screenY);
	}

	public interface WidthMeasurer {
		float measure(String s);
	}

	int[] screenLocation = {0, 0};
	boolean inFloaterDrag;
	boolean inLongClicked;
	int untouchableSideWidth = Integer.MIN_VALUE;
	FloaterDragListener floaterDragListener;

	// rawText is the logical label; the superclass renders a possibly line-broken display copy of it
	CharSequence rawText = "";
	int lastWrapWidth = -1;
	CharSequence lastWrapSource = null;
	boolean applyingWrap;

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
		setMaxLines(2);
		setEllipsize(TextUtils.TruncateAt.END);
	}

	@Override
	public void setText(final CharSequence text, final BufferType type) {
		if (!applyingWrap) {
			rawText = text == null ? "" : text;
			lastWrapSource = null;
		}
		super.setText(text, type);
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		final int availWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
		if (availWidth > 0 && (availWidth != lastWrapWidth || rawText != lastWrapSource)) {
			lastWrapWidth = availWidth;
			lastWrapSource = rawText;

			final String display = balanceWrap(rawText.toString(), availWidth, getPaint()::measureText);
			applyingWrap = true;
			super.setText(display, BufferType.NORMAL);
			applyingWrap = false;
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	public static String balanceWrap(final String text, final float availWidth, final WidthMeasurer measurer) {
		if (text.isEmpty() || measurer.measure(text) <= availWidth) {
			return text;
		}

		final int n = text.length();
		int bestSplit = -1;
		float bestMax = Float.MAX_VALUE;
		boolean foundFit = false;

		for (int i = 1; i < n; i++) {
			final String l1 = rtrim(text.substring(0, i));
			final String l2 = ltrim(text.substring(i));
			if (l1.isEmpty() || l2.isEmpty()) continue;

			final float w1 = measurer.measure(l1);
			final float w2 = measurer.measure(l2);
			final float mx = Math.max(w1, w2);
			final boolean fits = w1 <= availWidth && w2 <= availWidth;

			if (fits && !foundFit) {
				foundFit = true;
				bestMax = mx;
				bestSplit = i;
			} else if (fits == foundFit && mx < bestMax) {
				bestMax = mx;
				bestSplit = i;
			}
		}

		if (bestSplit < 0) {
			return text;
		}
		return rtrim(text.substring(0, bestSplit)) + "\n" + ltrim(text.substring(bestSplit));
	}

	private static String rtrim(final String s) {
		int end = s.length();
		while (end > 0 && s.charAt(end - 1) == ' ') end--;
		return s.substring(0, end);
	}

	private static String ltrim(final String s) {
		int start = 0;
		while (start < s.length() && s.charAt(start) == ' ') start++;
		return s.substring(start);
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		final int action = event.getActionMasked();

		float x = event.getX();
		float y = event.getY();

		if (action == MotionEvent.ACTION_DOWN) {
			if (untouchableSideWidth == Integer.MIN_VALUE) {
				untouchableSideWidth = getResources().getDimensionPixelSize(R.dimen.nav_prevnext_width) - getResources().getDimensionPixelSize(R.dimen.nav_goto_side_margin);
			}

			if (x >= 0 && x < untouchableSideWidth || x < getWidth() && x >= getWidth() - untouchableSideWidth) {
				return false;
			}
		}

		getLocationOnScreen(screenLocation);
		float screenX = x + screenLocation[0];
		float screenY = y + screenLocation[1];

		if (action == MotionEvent.ACTION_DOWN) { // reset long-clicked status
			inLongClicked = false;
		}

		if (!inLongClicked) { // do not continue if finger is still down but it's because long click is in progress
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
		}

		return super.onTouchEvent(event);
	}

	public void setFloaterDragListener(final FloaterDragListener floaterDragListener) {
		this.floaterDragListener = floaterDragListener;
	}

	@Override
	public boolean performLongClick() {
		inLongClicked = true;
		return super.performLongClick();
	}
}
