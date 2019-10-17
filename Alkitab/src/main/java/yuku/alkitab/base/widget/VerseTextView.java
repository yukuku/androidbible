package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Rect;
import androidx.core.view.MotionEventCompat;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;

import java.util.ArrayList;
import java.util.List;

public class VerseTextView extends TextView {
	public static final String TAG = VerseTextView.class.getSimpleName();

	static class SpanEntry {
		public Rect rect = new Rect();
		public ClickableSpan span;

		void clear() {
			rect.setEmpty();
			span = null;
		}
	}

	public static ThreadLocal<List<SpanEntry>> spanEntriesBuffer = new ThreadLocal<List<SpanEntry>>() {
		@Override
		protected List<SpanEntry> initialValue() {
			return new ArrayList<>(8);
		}
	};

	public VerseTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * Detects link clicks more accurately using the following algorithm:
	 * 1. Get all the clickable spans.
	 * 2. For each of the clickable spans, try to approximate the bounds rects of the span by:
	 *    a. If the span start and end are in the same line, the bounds rect is from span's start to end.
	 *    b. Else, there are three bounds rects: the span start until the right side, span end until the left side, and the big
	 *       multi-line bounds rect between the bottom of span start and top of span end. TODO: Support RTL.
	 * 3. Look for the entry that is nearest to the touch with max distance 24dp (so the touch diameter is 48dp) and perform click on the span.
	 *    If there is no such entry, make our handling return false to let the touch handled by this view's parent.
	 */
	@Override public boolean onTouchEvent(MotionEvent event) {
		final int action = MotionEventCompat.getActionMasked(event);
		if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN) return false;

		final CharSequence text = this.getText();
		if (!(text instanceof Spanned)) return false;

		final Layout layout = this.getLayout();
		if (layout == null) return false;

		final Spanned buffer = (Spanned) text;

		final int touchX = (int) (event.getX() + 0.5f) - this.getTotalPaddingLeft() + this.getScrollX();
		final int touchY = (int) (event.getY() + 0.5f) - this.getTotalPaddingTop() + this.getScrollY();

		final List<SpanEntry> spanEntries = spanEntriesBuffer.get();
		int spanEntries_count = 0; // we don't clear the list to prevent deallocation and reallocation of SpanEntries.

		for (final ClickableSpan span : buffer.getSpans(0, buffer.length(), ClickableSpan.class)) {
			final int spanStart = buffer.getSpanStart(span);
			final int lineStart = layout.getLineForOffset(spanStart);
			final int xStart = (int) (layout.getPrimaryHorizontal(spanStart) + 0.5f);

			final int spanEnd = buffer.getSpanEnd(span);
			final int lineEnd = layout.getLineForOffset(spanEnd);
			final int xEnd = (int) (layout.getPrimaryHorizontal(spanEnd) + 0.5f);

			if (lineStart == lineEnd) {
				final int top = layout.getLineTop(lineStart);
				final int bottom = layout.getLineBottom(lineStart);

				spanEntries_count = addSpanEntry(spanEntries, spanEntries_count, span, xStart, top, xEnd, bottom);
			} else {
				final int topStart = layout.getLineTop(lineStart);
				final int bottomStart = layout.getLineBottom(lineStart);
				final int topEnd = layout.getLineTop(lineEnd);
				final int bottomEnd = layout.getLineBottom(lineEnd);

				// line where span start is contained
				spanEntries_count = addSpanEntry(spanEntries, spanEntries_count, span, xStart, topStart, layout.getWidth(), bottomStart);

				// line where span end is contained
				spanEntries_count = addSpanEntry(spanEntries, spanEntries_count, span, 0, topEnd, xEnd, bottomEnd);

				// add the in-between span only if line difference is > 1
				if (lineEnd - lineStart > 1) {
					spanEntries_count = addSpanEntry(spanEntries, spanEntries_count, span, 0, bottomStart, layout.getWidth(), topEnd);
				}
			}
		}

		if (BuildConfig.DEBUG) {
			AppLog.d(TAG, "----------");
			AppLog.d(TAG, "touchX=" + touchX);
			AppLog.d(TAG, "touchY=" + touchY);

			for (int i = 0; i < spanEntries_count; i++) {
				final SpanEntry e = spanEntries.get(i);
				AppLog.d(TAG, "SpanEntry " + i + " at " + e.rect.toString() + ": span " + e.span + " '" + buffer.subSequence(buffer.getSpanStart(e.span), buffer.getSpanEnd(e.span)) + "'");
			}
		}

		if (spanEntries_count == 0) return false;

		final float density = getResources().getDisplayMetrics().density;
		final int maxDistanceSquared = (int) (24 * 24 * density * density); // radius 24dp
		ClickableSpan bestSpan = null;
		int bestDistanceSquared = Integer.MAX_VALUE;
		for (int i = 0; i < spanEntries_count; i++) {
			final SpanEntry spanEntry = spanEntries.get(i);

			// is touch inside the span rect?
			final Rect r = spanEntry.rect;
			if (r.contains(touchX, touchY)) {
				bestDistanceSquared = 0;
				bestSpan = spanEntry.span;
				break; // no possible better target
			} else {
				final int distanceSquared;
				if (touchY < r.top) {
					if (touchX < r.left) {
						distanceSquared = ds(touchX - r.left, touchY - r.top);
					} else if (touchX >= r.right) {
						distanceSquared = ds(touchX - r.right, touchY - r.top);
					} else { // on the top of bounds
						distanceSquared = ds(0, touchY - r.top);
					}
				} else if (touchY >= r.bottom) {
					if (touchX < r.left) {
						distanceSquared = ds(touchX - r.left, touchY - r.bottom);
					} else if (touchX >= r.right) {
						distanceSquared = ds(touchX - r.right, touchY - r.bottom);
					} else { // on the bottom of bounds
						distanceSquared = ds(0, touchY - r.bottom);
					}
				} else { // on the left or right of bounds
					if (touchX < r.left) {
						distanceSquared = ds(touchX - r.left, 0);
					} else {
						distanceSquared = ds(touchX - r.right, 0);
					}
				}

				if (distanceSquared <= maxDistanceSquared) {
					if (distanceSquared < bestDistanceSquared) {
						bestDistanceSquared = distanceSquared;
						bestSpan = spanEntry.span;
					}
				}
			}
		}

		for (int i = 0; i < spanEntries_count; i++) {
			final SpanEntry spanEntry = spanEntries.get(i);
			spanEntry.clear(); // don't keep any references to span!
		}

		if (BuildConfig.DEBUG) {
			final double dist = Math.sqrt(bestDistanceSquared);
			AppLog.d(TAG, "Best span is: " + bestSpan + " with distance " + dist + " (" + (dist / density) + "dp)");
		}

		if (bestSpan != null) {
			if (action == MotionEvent.ACTION_UP) {
				bestSpan.onClick(this);
			}
			return true;
		}

		return false;
	}

	private static int ds(final int dx, final int dy) {
		return dx * dx + dy * dy;
	}

	private static int addSpanEntry(final List<SpanEntry> spanEntries, int spanEntries_count, final ClickableSpan span, final int left, final int top, final int right, final int bottom) {
		final SpanEntry spanEntry;
		if (spanEntries.size() > spanEntries_count) {
			spanEntry = spanEntries.get(spanEntries_count);
		} else {
			spanEntry = new SpanEntry();
			spanEntries.add(spanEntry);
		}
		spanEntries_count++;

		spanEntry.rect.set(left, top, right, bottom);
		spanEntry.span = span;
		return spanEntries_count;
	}
}
