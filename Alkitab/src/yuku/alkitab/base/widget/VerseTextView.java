package yuku.alkitab.base.widget;

import android.content.Context;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import yuku.afw.D;

public class VerseTextView extends TextView {
	public static final String TAG = VerseTextView.class.getSimpleName();

	public VerseTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override public boolean onTouchEvent(MotionEvent event) {
		TextView widget = (TextView) this;
		CharSequence text = widget.getText();
		if (text instanceof Spanned) {
			Spanned buffer = (Spanned) text;

			int action = event.getAction();

			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
				int x = (int) event.getX();
				int y = (int) event.getY();

				x -= widget.getTotalPaddingLeft();
				y -= widget.getTotalPaddingTop();

				x += widget.getScrollX();
				y += widget.getScrollY();

				Layout layout = widget.getLayout();
				int line = layout.getLineForVertical(y);
				int off = layout.getOffsetForHorizontal(line, x);

				if (D.EBUG) {
					Log.d(TAG, "----------");
					Log.d(TAG, "y=" + y + " line=" + line);
					Log.d(TAG, "x=" + x + " off=" + off);
					String t = getText().toString();
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
				
				ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

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
							link[0].onClick(widget);
							return true;
						} else if (action == MotionEvent.ACTION_DOWN) {
							if (buffer instanceof Spannable) {
								Selection.setSelection((Spannable) buffer, buffer.getSpanStart(link[0]), buffer.getSpanEnd(link[0]));
							}
							return true;
						}
					}
				}
			}
		}

		return false;
	}
}
