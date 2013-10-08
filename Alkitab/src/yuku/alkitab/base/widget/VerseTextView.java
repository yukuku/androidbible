package yuku.alkitab.base.widget;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;
import yuku.alkitab.BuildConfig;

import java.util.ArrayList;
import java.util.List;

public class VerseTextView extends TextView {
	public static final String TAG = VerseTextView.class.getSimpleName();

	static class SpanEntry {
		public int x;
		public int y;
		public ClickableSpan span;

		void clear() {
			x = 0;
			y = 0;
			span = null;
		}
	}

	public static ThreadLocal<List<SpanEntry>> spanEntriesBuffer = new ThreadLocal<List<SpanEntry>>() {
		@Override
		protected List<SpanEntry> initialValue() {
			return new ArrayList<SpanEntry>(8);
		}
	};

	public VerseTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * Detects link clicks more accurately using the following algorithm:
	 * 1. Get all the clickable spans.
	 * 2. For each of the clickable spans, get the (x, y) coordinates of the start and the end of span and store them in a reusable collection.
	 *    An entry in the collection is: (x, y, span).
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
			final int yStart = (layout.getLineTop(lineStart) + layout.getLineBottom(lineStart)) / 2;
			spanEntries_count = addSpanEntry(spanEntries, spanEntries_count, span, xStart, yStart);

			final int spanEnd = buffer.getSpanEnd(span);
			final int lineEnd = layout.getLineForOffset(spanEnd);
			final int xEnd = (int) (layout.getPrimaryHorizontal(spanEnd) + 0.5f);
			final int yEnd = (layout.getLineTop(lineEnd) + layout.getLineBottom(lineEnd)) / 2;
			spanEntries_count = addSpanEntry(spanEntries, spanEntries_count, span, xEnd, yEnd);
		}

		if (BuildConfig.DEBUG) {
			Log.d(TAG, "----------");
			Log.d(TAG, "touchX=" + touchX);
			Log.d(TAG, "touchY=" + touchY);

			for (int i = 0; i < spanEntries_count; i++) {
				final SpanEntry spanEntry = spanEntries.get(i);
				Log.d(TAG, "SpanEntry " + i + " at (" + spanEntry.x + ", " + spanEntry.y + "): span " + spanEntry.span + " '" + buffer.subSequence(buffer.getSpanStart(spanEntry.span), buffer.getSpanEnd(spanEntry.span)) + "'");
			}
		}

		if (spanEntries_count == 0) return false;

		final float density = getResources().getDisplayMetrics().density;
		final int maxDistanceSquared = (int) (24 * 24 * density * density); // radius 24dp
		ClickableSpan bestSpan = null;
		int bestDistanceSquared = Integer.MAX_VALUE;
		for (int i = 0; i < spanEntries_count; i++) {
			final SpanEntry spanEntry = spanEntries.get(i);
			final int deltaX = touchX - spanEntry.x;
			final int deltaY = touchY - spanEntry.y;
			final int distanceSquared = deltaX * deltaX + deltaY * deltaY;
			if (distanceSquared <= maxDistanceSquared) {
				if (distanceSquared < bestDistanceSquared) {
					bestDistanceSquared = distanceSquared;
					bestSpan = spanEntry.span;
				}
			}
			spanEntry.clear(); // don't keep any references to span!
		}

		if (bestSpan != null) {
			if (action == MotionEvent.ACTION_UP) {
				bestSpan.onClick(this);
			}
			return true;
		}

		return false;
	}

	private int addSpanEntry(final List<SpanEntry> spanEntries, int spanEntries_count, final ClickableSpan span, final int x, final int y) {
		final SpanEntry spanEntry;
		if (spanEntries.size() > spanEntries_count) {
			spanEntry = spanEntries.get(spanEntries_count);
		} else {
			spanEntry = new SpanEntry();
			spanEntries.add(spanEntry);
		}
		spanEntries_count++;

		spanEntry.x = x;
		spanEntry.y = y;
		spanEntry.span = span;
		return spanEntries_count;
	}
}
