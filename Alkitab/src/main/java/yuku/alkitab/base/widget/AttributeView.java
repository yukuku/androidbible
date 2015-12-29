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

	static Bitmap originalBookmarkBitmap = null;
	static Bitmap scaledBookmarkBitmap = null;
	static Bitmap originalNoteBitmap = null;
	static Bitmap scaledNoteBitmap = null;
	static Bitmap[] originalProgressMarkBitmaps = new Bitmap[PROGRESS_MARK_TOTAL_COUNT];
	static Bitmap[] scaledProgressMarkBitmaps = new Bitmap[PROGRESS_MARK_TOTAL_COUNT];
	static Bitmap originalHasMapsBitmap = null;
	static Bitmap scaledHasMapsBitmap = null;
	static Paint alphaPaint = new Paint();
	static Paint attributeCountPaintBookmark;
    static Paint attributeCountPaintNote;

    static {
		attributeCountPaintBookmark = new Paint();
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
	float scale = 2.f;

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

	static Bitmap scale(Bitmap original, float scale) {
		if (scale == 1.f) {
			return Bitmap.createBitmap(original);
		}

		final boolean filter = !(scale == 2.f || scale == 3.f || scale == 4.f);
		return Bitmap.createScaledBitmap(original, Math.round(original.getWidth() * scale), Math.round(original.getHeight() * scale), filter);
	}

	Bitmap getScaledBookmarkBitmap() {
		if (originalBookmarkBitmap == null) {
			originalBookmarkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_attr_bookmark);
		}

		if (scaledBookmarkBitmap == null || scaledBookmarkBitmap.getWidth() != Math.round(originalBookmarkBitmap.getWidth() * scale)) {
			scaledBookmarkBitmap = scale(originalBookmarkBitmap, scale);
		}

		return scaledBookmarkBitmap;
	}

	Bitmap getScaledNoteBitmap() {
		if (originalNoteBitmap == null) {
			originalNoteBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_attr_note);
		}

		if (scaledNoteBitmap == null || scaledBookmarkBitmap.getWidth() != Math.round(originalNoteBitmap.getWidth() * scale)) {
			scaledNoteBitmap = scale(originalNoteBitmap, scale);
		}

		return scaledNoteBitmap;
	}

	Bitmap getScaledProgressMarkBitmapByPresetId(int preset_id) {
		if (originalProgressMarkBitmaps[preset_id] == null) {
			originalProgressMarkBitmaps[preset_id] = BitmapFactory.decodeResource(getResources(), getProgressMarkIconResource(preset_id));
		}

		if (scaledProgressMarkBitmaps[preset_id] == null || scaledProgressMarkBitmaps[preset_id].getWidth() != Math.round(originalProgressMarkBitmaps[preset_id].getWidth() * scale)) {
			scaledProgressMarkBitmaps[preset_id] = scale(originalProgressMarkBitmaps[preset_id], scale);
		}

		return scaledProgressMarkBitmaps[preset_id];
	}

	Bitmap getScaledHasMapsBitmap() {
		if (originalHasMapsBitmap == null) {
			originalHasMapsBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_attr_has_maps);
		}

		if (scaledHasMapsBitmap == null || scaledHasMapsBitmap.getWidth() != Math.round(originalHasMapsBitmap.getWidth() * scale)) {
			scaledHasMapsBitmap = scale(originalHasMapsBitmap, scale);
		}

		return scaledHasMapsBitmap;
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		int totalHeight = 0;
		int totalWidth = 0;
		if (bookmark_count > 0) {
			final Bitmap b = getScaledBookmarkBitmap();
			totalHeight += b.getHeight();
			totalWidth = Math.max(totalWidth, b.getWidth());
		}
		if (note_count > 0) {
			final Bitmap b = getScaledNoteBitmap();
			totalHeight += b.getHeight();
			totalWidth = Math.max(totalWidth, b.getWidth());
		}
		if (progress_mark_bits != 0) {
			for (int preset_id = 0; preset_id < PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
				if (isProgressMarkSetFromAttribute(preset_id)) {
					final Bitmap b = getScaledProgressMarkBitmapByPresetId(preset_id);
					totalHeight += b.getHeight();
					totalWidth = Math.max(totalWidth, b.getWidth());
				}
			}
		}
		if (has_maps) {
			final Bitmap b = getScaledHasMapsBitmap();
			totalHeight += b.getHeight();
			totalWidth = Math.max(totalWidth, b.getWidth());
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
			final Bitmap b = getScaledBookmarkBitmap();
			canvas.drawBitmap(b, drawOffsetLeft, totalHeight, null);
			if (bookmark_count > 1) {
				canvas.drawText(String.valueOf(bookmark_count), drawOffsetLeft + b.getWidth() / 2, totalHeight + b.getHeight() * 3 / 4, attributeCountPaintBookmark);
			}
			totalHeight += b.getHeight();
		}
		if (note_count > 0) {
			final Bitmap b = getScaledNoteBitmap();
			canvas.drawBitmap(b, drawOffsetLeft, totalHeight, null);
			if (note_count > 1) {
				canvas.drawText(String.valueOf(note_count), drawOffsetLeft + b.getWidth() / 2, totalHeight + b.getHeight() * 7 / 10, attributeCountPaintNote);
			}
			totalHeight += b.getHeight();
		}
		if (progress_mark_bits != 0) {
			for (int preset_id = 0; preset_id < PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
				if (isProgressMarkSetFromAttribute(preset_id)) {
					final Bitmap b = getScaledProgressMarkBitmapByPresetId(preset_id);
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
					canvas.drawBitmap(b, 0, totalHeight, p);
					totalHeight += b.getHeight();
				}
			}
		}
		if (has_maps) {
			final Bitmap b = getScaledHasMapsBitmap();
			canvas.drawBitmap(b, drawOffsetLeft, totalHeight, null);
			//noinspection UnusedAssignment
			totalHeight += b.getHeight();
		}
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		final int action = MotionEventCompat.getActionMasked(event);
		if (action == MotionEvent.ACTION_UP) {
			int totalHeight = 0;
			final float y = event.getY();
			if (bookmark_count > 0) {
				final Bitmap b = getScaledBookmarkBitmap();
				totalHeight += b.getHeight();
				if (totalHeight > y) {
					attributeListener.onBookmarkAttributeClick(ari);
					return true;
				}
			}
			if (note_count > 0) {
				final Bitmap b = getScaledNoteBitmap();
				totalHeight += b.getHeight();
				if (totalHeight > y) {
					attributeListener.onNoteAttributeClick(ari);
					return true;
				}
			}
			if (progress_mark_bits != 0) {
				for (int preset_id = 0; preset_id < PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
					if (isProgressMarkSetFromAttribute(preset_id)) {
						final Bitmap b = getScaledProgressMarkBitmapByPresetId(preset_id);
						totalHeight += b.getHeight();
						if (totalHeight > y) {
							attributeListener.onProgressMarkAttributeClick(preset_id);
							return true;
						}
					}
				}
			}
			if (has_maps) {
				final Bitmap b = getScaledHasMapsBitmap();
				totalHeight += b.getHeight();
				if (totalHeight > y) {
					attributeListener.onHasMapsAttributeClick(ari);
					return true;
				}
			}
		} else if (action == MotionEvent.ACTION_DOWN) {
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
