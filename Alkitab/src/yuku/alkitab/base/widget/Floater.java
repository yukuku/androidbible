package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.Version;

public class Floater extends View {
	public static final String TAG = Floater.class.getSimpleName();

	Paint passiveBookPaint;
	Paint activeBookPaint;
	Version version;
	int activeBookIndex = -1;
	private float density;

	public Floater(final Context context) {
		super(context);
		init();
	}

	public Floater(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public Floater(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init() {
		density = getResources().getDisplayMetrics().density;

		passiveBookPaint = new Paint();
		passiveBookPaint.setAntiAlias(true);
		passiveBookPaint.setColor(0xff000000);
		passiveBookPaint.setShadowLayer(2 * density, 0, 0, 0xffffffff);
		passiveBookPaint.setTextSize(16 * density);

		activeBookPaint = new Paint();
		activeBookPaint.setAntiAlias(true);
		activeBookPaint.setColor(0xffff0000);
		activeBookPaint.setTypeface(Typeface.DEFAULT_BOLD);
		activeBookPaint.setTextSize(16 * density);
		activeBookPaint.setShadowLayer(4 * density, 0, 0, 0xff00ffff);
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		final float w = getWidth() - getPaddingLeft() - getPaddingRight();
		final float h = getHeight() - getPaddingTop() - getPaddingBottom();

		if (version == null) return;

		canvas.drawColor(0x90ffffff);

		final Book[] books = version.getConsecutiveBooks();
		final int book_count = books.length;
		final int rows = (book_count + 1) / 2;

		// passive books
		for (int i = 0; i < book_count; i++) {
			final Book book = books[i];
			if (activeBookIndex != i) {
				final boolean leftColumn = i < rows;
				if (leftColumn) {
					drawBookOnLeftColumn(canvas, passiveBookPaint, w, h, rows, i, book);
				} else {
					drawBookOnRightColumn(canvas, passiveBookPaint, w, h, rows, i, book);
				}
			}
		}

		// active book
		if (activeBookIndex != -1) {
			final boolean leftColumn = activeBookIndex < rows;
			if (leftColumn) {
				drawBookOnLeftColumn(canvas, activeBookPaint, w, h, rows, activeBookIndex, books[activeBookIndex]);
			} else {
				drawBookOnRightColumn(canvas, activeBookPaint, w, h, rows, activeBookIndex, books[activeBookIndex]);
			}
		}

		// passive chapters

	}

	private void drawBookOnLeftColumn(final Canvas canvas, final Paint paint, final float w, final float h, final int rows, final int i, final Book book) {
		canvas.drawText(book.shortName, getPaddingLeft(), getPaddingTop() + (i + 1) * (h / rows), paint);
	}

	private void drawBookOnRightColumn(final Canvas canvas, final Paint paint, final float w, final float h, final int rows, final int i, final Book book) {
		canvas.drawText(book.shortName, getPaddingLeft() + w / 2, getPaddingTop() + (i + 1 - rows) * (h / rows), paint);
	}

	public void onDragStart(final Version version) {
		this.version = version;
		this.activeBookIndex = -1;
	}

	public void onDragMove(final float x, final float y) {
		Log.d(TAG, "move x=" + x + " y=" + y);

		final float w = getWidth() - getPaddingLeft() - getPaddingRight();
		final float h = getHeight() - getPaddingTop() - getPaddingBottom();

		if (y < getPaddingTop() || y > getPaddingTop() + h) {
			activeBookIndex = -1;
		} else {
			final int book_count = version.getConsecutiveBooks().length;
			final int rows = (book_count + 1) / 2;
			final boolean leftColumn = x < getPaddingLeft() + w / 2;
			if (leftColumn) {
				activeBookIndex = (int) ((y - getPaddingTop()) / h * rows);
			} else {
				activeBookIndex = rows + (int) ((y - getPaddingTop()) / h * rows);
			}
			// final check for odd book_count
			if (activeBookIndex >= book_count) {
				activeBookIndex = -1;
			}
		}

		invalidate();
	}

	public void onDragComplete(final float x, final float y) {
		Log.d(TAG, "complete x=" + x + " y=" + y);

		this.version = null; // prevent holding of memory
	}
}
