package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import yuku.alkitab.R;
import yuku.alkitab.base.model.Book;

public class AttributeView extends View {

	static Bitmap bookmarkBitmap = null;
	static Bitmap noteBitmap = null;
	static Bitmap[] progressMarkBitmap = new Bitmap[5];
	static int[] progressMarkResources = {
	R.drawable.ic_attr_progress_mark_1,
	R.drawable.ic_attr_progress_mark_2,
	R.drawable.ic_attr_progress_mark_3,
	R.drawable.ic_attr_progress_mark_4,
	R.drawable.ic_attr_progress_mark_5
	};

	private boolean showBookmark;
	private boolean showNote;
	private boolean[] showProgressMarks;

	private VersesView.AttributeListener attributeListener;
	private Book book;
	private int chapter_1;
	private int verse_1;

	public AttributeView(final Context context) {
		super(context);
	}

	public AttributeView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}

	public void showBookmark(final boolean showBookmark) {
		this.showBookmark = showBookmark;
		requestLayout();
		invalidate();
	}

	public void showNote(final boolean showNote) {
		this.showNote = showNote;
		requestLayout();
		invalidate();
	}

	public void showProgressMarks(final boolean[] showProgressMarks) {
		this.showProgressMarks = showProgressMarks;
		requestLayout();
		invalidate();
	}


	Bitmap getBookmarkBitmap() {
		if (bookmarkBitmap == null) {
			bookmarkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_attr_bookmark);
		}
		return bookmarkBitmap;
	}

	Bitmap getNoteBitmap() {
		if (noteBitmap == null) {
			noteBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_attr_note);
		}
		return noteBitmap;
	}

	Bitmap getProgressMarkBitmapById(int progressId) {
		if (progressMarkBitmap[progressId] == null) {
			progressMarkBitmap[progressId] = BitmapFactory.decodeResource(getResources(), progressMarkResources[progressId]);
		}
		return progressMarkBitmap[progressId];
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		int totalHeight = 0;
		int totalWidth = 0;
		if (showBookmark) {
			final Bitmap bookmarkBitmap = getBookmarkBitmap();
			totalHeight += bookmarkBitmap.getHeight();
			if (totalWidth < bookmarkBitmap.getWidth()) {
				totalWidth = bookmarkBitmap.getWidth();
			}
		}
		if (showNote) {
			final Bitmap noteBitmap = getNoteBitmap();
			totalHeight += noteBitmap.getHeight();
			if (totalWidth < noteBitmap.getWidth()) {
				totalWidth = noteBitmap.getWidth();
			}
		}
		if (showProgressMarks != null) {
			for (int i = 0; i < showProgressMarks.length; i++) {
				if (showProgressMarks[i]) {
					final Bitmap progressMarkBitmapById = getProgressMarkBitmapById(i);
					totalHeight += progressMarkBitmapById.getHeight();
					if (totalWidth < progressMarkBitmapById.getWidth()) {
						totalWidth = progressMarkBitmapById.getWidth();
					}
				}
			}
		}


		setMeasuredDimension(totalWidth, totalHeight);
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		int totalHeight = 0;
		if (showBookmark) {
			final Bitmap bookmarkBitmap = getBookmarkBitmap();
			canvas.drawBitmap(bookmarkBitmap, 2, totalHeight, null);
			totalHeight += bookmarkBitmap.getHeight();
		}
		if (showNote) {
			final Bitmap noteBitmap = getNoteBitmap();
			canvas.drawBitmap(noteBitmap, 2, totalHeight, null);
			totalHeight += noteBitmap.getHeight();
		}
		if (showProgressMarks != null) {
			for (int i = 0; i < showProgressMarks.length; i++) {
				if (showProgressMarks[i]) {
					final Bitmap progressMarkBitmapById = getProgressMarkBitmapById(i);
					canvas.drawBitmap(progressMarkBitmapById, 0, totalHeight, null);
					totalHeight += progressMarkBitmapById.getHeight();
				}
			}
		}
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		float y = event.getY();
		int totalHeight = 0;
		if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_UP) {
			if (showBookmark) {
				final Bitmap bookmarkBitmap = getBookmarkBitmap();
				totalHeight += bookmarkBitmap.getHeight();
				if (totalHeight > y) {
					attributeListener.onBookmarkAttributeClick(book, chapter_1, verse_1);
					return true;
				}
			}
			if (showNote) {
				final Bitmap noteBitmap = getNoteBitmap();
				totalHeight += noteBitmap.getHeight();
				if (totalHeight > y) {
					attributeListener.onNoteAttributeClick(book, chapter_1, verse_1);
					return true;
				}
			}
			if (showProgressMarks != null) {
				for (int i = 0; i < showProgressMarks.length; i++) {
					if (showProgressMarks[i]) {
						final Bitmap progressMarkBitmapById = getProgressMarkBitmapById(i);
						totalHeight += progressMarkBitmapById.getHeight();
						if (totalHeight > y) {
							attributeListener.onProgressMarkAttributeClick(i);
							return true;
						}
					}
				}
			}
		} else if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
			return true;
		}
		return false;
	}

	public interface OnItemClickListener {
		public void onItemClick();
	}

	public void setAttributeListener(VersesView.AttributeListener attributeListener, Book book, int chapter_1, int verse_1) {
		this.attributeListener = attributeListener;
		this.book = book;
		this.chapter_1 = chapter_1;
		this.verse_1 = verse_1;
	}
}
