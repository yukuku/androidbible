package yuku.alkitab.base.widget;

import android.content.Context;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;

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

				ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

				if (link.length != 0) {
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

		return false;
	}
}
