package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import yuku.alkitab.base.App;
import yuku.alkitab.debug.R;

public class AttributeView extends View {

	public static final int PROGRESS_MARK_BITS_START = 8;
	public static final int PROGRESS_MARK_TOTAL_COUNT = 5;
	public static final int PROGRESS_MARK_BIT_MASK = (1 << PROGRESS_MARK_BITS_START) * ((1 << PROGRESS_MARK_TOTAL_COUNT) - 1);

	static Bitmap bookmarkBitmap = null;
	static Bitmap noteBitmap = null;
	static Bitmap[] progressMarkBitmap = new Bitmap[PROGRESS_MARK_TOTAL_COUNT];
	static Bitmap hasMapsBitmap = null;
	static Paint alphaPaint = new Paint();
	static Paint attributeCountPaintBookmark = new Paint();
    static Paint attributeCountPaintNote;

    static {
        attributeCountPaintBookmark.setTypeface(Typeface.DEFAULT_BOLD);
        attributeCountPaintBookmark.setColor(0xff000000);
        attributeCountPaintBookmark.setTextSize(App.context.getResources().getDisplayMetrics().density * 12.f);
        attributeCountPaintBookmark.setAntiAlias(true);
        attributeCountPaintBookmark.setTextAlign(Paint.Align.CENTER);

        attributeCountPaintNote = new Paint(attributeCountPaintBookmark);
    }

	int bookmark_count;
	int note_count;
	int progress_mark_bits;
	boolean has_maps;

	private VersesView.AttributeListener attributeListener;
	private int ari;

	private static SparseArray<Long> progressMarkAnimationStartTimes = new SparseArray<>();
	private int drawOffsetLeft;

	public AttributeView(final Context context) {
		super(context);
		init();
	}

	public AttributeView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
        float density = getResources().getDisplayMetrics().density;

		drawOffsetLeft = Math.round(1 * density);
        attributeCountPaintNote.setShadowLayer(density * 4, 0, 0, 0xffffffff);
	}

	public int getBookmarkCount() {
		return bookmark_count;
	}

	public void setBookmarkCount(final int bookmark_count) {
		this.bookmark_count = bookmark_count;
		requestLayout();
		invalidate();
	}

	public int getNoteCount() {
		return note_count;
	}

	public void setNoteCount(final int note_count) {
		this.note_count = note_count;
		requestLayout();
		invalidate();
	}

	public int getProgressMarkBits() {
		return progress_mark_bits;
	}

	public void setProgressMarkBits(final int progress_mark_bits) {
		this.progress_mark_bits = progress_mark_bits;
		requestLayout();
		invalidate();
	}

	public boolean getHasMaps() {
		return has_maps;
	}

	public void setHasMaps(final boolean has_maps) {
		this.has_maps = has_maps;
		requestLayout();
		invalidate();
	}

	public boolean isShowingSomething() {
		return bookmark_count > 0 || note_count > 0 || ((progress_mark_bits & PROGRESS_MARK_BIT_MASK) != 0);
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

	Bitmap getHasMapsBitmap() {
		if (hasMapsBitmap == null) {
			hasMapsBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_attr_has_maps);
		}
		return hasMapsBitmap;
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		int totalHeight = 0;
		int totalWidth = 0;
		if (bookmark_count > 0) {
			final Bitmap bookmarkBitmap = getBookmarkBitmap();
			totalHeight += bookmarkBitmap.getHeight();
			if (totalWidth < bookmarkBitmap.getWidth()) {
				totalWidth = bookmarkBitmap.getWidth();
			}
		}
		if (note_count > 0) {
			final Bitmap noteBitmap = getNoteBitmap();
			totalHeight += noteBitmap.getHeight();
			if (totalWidth < noteBitmap.getWidth()) {
				totalWidth = noteBitmap.getWidth();
			}
		}
		if (progress_mark_bits != 0) {
			for (int preset_id = 0; preset_id < PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
				if (isProgressMarkSetFromAttribute(preset_id)) {
					final Bitmap progressMarkBitmapById = getProgressMarkBitmapByPresetId(preset_id);
					totalHeight += progressMarkBitmapById.getHeight();
					if (totalWidth < progressMarkBitmapById.getWidth()) {
						totalWidth = progressMarkBitmapById.getWidth();
					}
				}
			}
		}
		if (has_maps) {
			final Bitmap hasMapsBitmap = getHasMapsBitmap();
			totalHeight += hasMapsBitmap.getHeight();
			if (totalWidth < hasMapsBitmap.getWidth()) {
				totalWidth = hasMapsBitmap.getWidth();
			}
		}

		setMeasuredDimension(totalWidth, totalHeight);
	}

	private boolean isProgressMarkSetFromAttribute(final int preset_id) {
		return (progress_mark_bits & (1 << (preset_id + PROGRESS_MARK_BITS_START))) != 0;
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		int totalHeight = 0;
		if (bookmark_count > 0) {
			final Bitmap bookmarkBitmap = getBookmarkBitmap();
			canvas.drawBitmap(bookmarkBitmap, drawOffsetLeft, totalHeight, null);
			if (bookmark_count > 1) {
				canvas.drawText(String.valueOf(bookmark_count), drawOffsetLeft + bookmarkBitmap.getWidth() / 2, totalHeight + bookmarkBitmap.getHeight() * 3/4, attributeCountPaintBookmark);
			}
			totalHeight += bookmarkBitmap.getHeight();
		}
		if (note_count > 0) {
			final Bitmap noteBitmap = getNoteBitmap();
			canvas.drawBitmap(noteBitmap, drawOffsetLeft, totalHeight, null);
			if (note_count > 1) {
				canvas.drawText(String.valueOf(note_count), drawOffsetLeft + noteBitmap.getWidth() / 2, totalHeight + noteBitmap.getHeight() * 7/10, attributeCountPaintNote);
			}
			totalHeight += noteBitmap.getHeight();
		}
		if (progress_mark_bits != 0) {
			for (int preset_id = 0; preset_id < PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
				if (isProgressMarkSetFromAttribute(preset_id)) {
					final Bitmap progressMarkBitmapById = getProgressMarkBitmapByPresetId(preset_id);
					final Long animationStartTime = progressMarkAnimationStartTimes.get(preset_id);
					final Paint p;
					if (animationStartTime == null) {
						p = null;
					} else {
						final int animationElapsed = (int) (System.currentTimeMillis() - animationStartTime);
						final int animationDuration = 800;
						if (animationElapsed >= animationDuration) {
							p = null;
						} else {
							alphaPaint.setAlpha(animationElapsed * 255 / animationDuration);
							p = alphaPaint;
							invalidate(); // animation is still running so request for invalidate
						}
					}
					canvas.drawBitmap(progressMarkBitmapById, 0, totalHeight, p);
					totalHeight += progressMarkBitmapById.getHeight();
				}
			}
		}
		if (has_maps) {
			final Bitmap hasMapsBitmap = getHasMapsBitmap();
			canvas.drawBitmap(hasMapsBitmap, drawOffsetLeft, totalHeight, null);
			//noinspection UnusedAssignment
			totalHeight += hasMapsBitmap.getHeight();
		}
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		float y = event.getY();
		int totalHeight = 0;
		if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_UP) {
			if (bookmark_count > 0) {
				final Bitmap bookmarkBitmap = getBookmarkBitmap();
				totalHeight += bookmarkBitmap.getHeight();
				if (totalHeight > y) {
					attributeListener.onBookmarkAttributeClick(ari);
					return true;
				}
			}
			if (note_count > 0) {
				final Bitmap noteBitmap = getNoteBitmap();
				totalHeight += noteBitmap.getHeight();
				if (totalHeight > y) {
					attributeListener.onNoteAttributeClick(ari);
					return true;
				}
			}
			if (progress_mark_bits != 0) {
				for (int preset_id = 0; preset_id < PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
					if (isProgressMarkSetFromAttribute(preset_id)) {
						final Bitmap progressMarkBitmapById = getProgressMarkBitmapByPresetId(preset_id);
						totalHeight += progressMarkBitmapById.getHeight();
						if (totalHeight > y) {
							attributeListener.onProgressMarkAttributeClick(preset_id);
							return true;
						}
					}
				}
			}
			if (has_maps) {
				final Bitmap hasMapsBitmap = getHasMapsBitmap();
				totalHeight += hasMapsBitmap.getHeight();
				if (totalHeight > y) {
					attributeListener.onNoteAttributeClick(ari);
					return true;
				}
			}
		} else if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
			return true;
		}
		return false;
	}

	public void setAttributeListener(VersesView.AttributeListener attributeListener, int ari) {
		this.attributeListener = attributeListener;
		this.ari = ari;
	}

	public static void startAnimationForProgressMark(final int preset_id) {
		progressMarkAnimationStartTimes.put(preset_id, System.currentTimeMillis());
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
