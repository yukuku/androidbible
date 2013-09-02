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

	private boolean showBookmark;
	private boolean showNote;
	private int attribute;

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

	public void showProgressMarks(final int attribute) {
		this.attribute = attribute;
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

	Bitmap getProgressMarkBitmapByPresetId(int preset_id) {
		if (progressMarkBitmap[preset_id] == null) {
			progressMarkBitmap[preset_id] = BitmapFactory.decodeResource(getResources(), getProgressMarkIconResource(preset_id));
		}
		return progressMarkBitmap[preset_id];
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
		if (attribute != 0) {
			for (int i = 0; i < 5; i++) {
				if (isProgressMarkSetFromAttribute(i)) {
					final Bitmap progressMarkBitmapById = getProgressMarkBitmapByPresetId(i);
					totalHeight += progressMarkBitmapById.getHeight();
					if (totalWidth < progressMarkBitmapById.getWidth()) {
						totalWidth = progressMarkBitmapById.getWidth();
					}
				}
			}
		}

		setMeasuredDimension(totalWidth, totalHeight);
	}

	private boolean isProgressMarkSetFromAttribute(final int preset_id) {
		return (attribute & (1 << (preset_id + 8))) != 0;
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
		if (attribute != 0) {
			for (int i = 0; i < 5; i++) {
				if (isProgressMarkSetFromAttribute(i)) {
					final Bitmap progressMarkBitmapById = getProgressMarkBitmapByPresetId(i);
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
			if (attribute != 0) {
				for (int i = 0; i < 5; i++) {
					if (isProgressMarkSetFromAttribute(i)) {
						final Bitmap progressMarkBitmapById = getProgressMarkBitmapByPresetId(i);
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

	public void setAttributeListener(VersesView.AttributeListener attributeListener, Book book, int chapter_1, int verse_1) {
		this.attributeListener = attributeListener;
		this.book = book;
		this.chapter_1 = chapter_1;
		this.verse_1 = verse_1;
	}

	public static int getDefaultProgressMarkStringResource(int preset_id) {
		switch (preset_id) {
			case 0:
				return R.string.pm_progress_1;
			case 1:
				return R.string.pm_progress_2;
			case 2:
				return R.string.pm_progress_3;
			case 3:
				return R.string.pm_progress_4;
			case 4:
				return R.string.pm_progress_5;
		}
		return 0;
	}

	public static int getProgressMarkIconResource(int preset_id) {
		switch (preset_id) {
			case 0:
				return R.drawable.ic_attr_progress_mark_1;
			case 1:
				return R.drawable.ic_attr_progress_mark_2;
			case 2:
				return R.drawable.ic_attr_progress_mark_3;
			case 3:
				return R.drawable.ic_attr_progress_mark_4;
			case 4:
				return R.drawable.ic_attr_progress_mark_5;
		}
		return 0;
	}
}
