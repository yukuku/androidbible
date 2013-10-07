package yuku.alkitab.base.widget;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;
import yuku.alkitab.BuildConfig;

public class VerseTextView extends TextView {
	public static final String TAG = VerseTextView.class.getSimpleName();

	public VerseTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override public boolean onTouchEvent(MotionEvent event) {
		final CharSequence text = this.getText();
		if (!(text instanceof Spanned)) return false;

		final Spanned buffer = (Spanned) text;
		final int action = MotionEventCompat.getActionMasked(event);

		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
			final int x = (int) (event.getX() + 0.5f) - this.getTotalPaddingLeft() + this.getScrollX();
			final int y = (int) (event.getY() + 0.5f) - this.getTotalPaddingTop() + this.getScrollY();

			final Layout layout = this.getLayout();
			if (layout == null) return false;

			final int line = layout.getLineForVertical(y);
			final int off = layout.getOffsetForHorizontal(line, x);

			if (BuildConfig.DEBUG) {
				Log.d(TAG, "----------");
				Log.d(TAG, "y=" + y + " line=" + line);
				Log.d(TAG, "x=" + x + " off=" + off);
				final String t = buffer.toString();
				if (off - 1 >= 0) {
					Log.d(TAG, "char offset " + (off-1) + ": " + t.charAt(off-1) + " 0x" + Integer.toHexString(t.charAt(off-1)));
				} else {
					Log.d(TAG, "char offset " + (off-1) + " is oob");
				}
				if (off < t.length()) {
					Log.d(TAG, "char offset " + (off) + ": " + t.charAt(off) + " 0x" + Integer.toHexString(t.charAt(off)));
				} else {
					Log.d(TAG, "char offset " + (off) + " is oob");
				}
			}

			final ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

			if (link.length != 0) {
				// prevent this bug from happening
				// [link] (end of text)      (clicked here)
				// but still link is considered clicked.
				// we don't allow x position of click further than 24dp away.
				float density = getResources().getDisplayMetrics().density;
				float xStart = layout.getPrimaryHorizontal(off);
				float xEnd = (off+1) >= buffer.length() ? layout.getPrimaryHorizontal(off) + 30 * density : layout.getPrimaryHorizontal(off + 1);
				float tolerance = 24 * density;

				boolean cancelClick = false;
				if (x < xStart - tolerance) {
					cancelClick = true;
				} else if (x > xEnd + tolerance) {
					cancelClick = true;
				}

				if (!cancelClick) {
					if (action == MotionEvent.ACTION_UP) {
						link[0].onClick(this);
					}
					return true;
				}
			}
		}

		return false;
	}
}
