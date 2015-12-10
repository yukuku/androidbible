package yuku.alkitab.base.widget;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.graphics.ColorUtils;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Checkable;
import android.widget.RelativeLayout;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.ProgressMark;

import java.util.Date;

public class VerseItem extends RelativeLayout implements Checkable {
	public static final String PROGRESS_MARK_DRAG_MIME_TYPE = "application/vnd.yuku.alkitab.progress_mark.drag";

	private static final float ATTENTION_DURATION = 2000L;

	private boolean checked;
	private static Paint checkedPaintSolid;
	private boolean collapsed;
	private boolean dragHover;
	private Drawable dragHoverBg;

	public VerseTextView lText;
	public TextView lVerseNumber;
	public AttributeView attributeView;

	/** the ari of the verse represented by this view. If this is 0, this is a pericope or something else. */
	private int ari;

	/**
	 * Whether we briefly "color" this verse resulting from navigation from other parts of the app (e.g. verse navigation, search results).
	 * If the value is 0, it means do not color. If nonzero, it is the starting time when the animation starts.
	 */
	private long attentionStart;
	private static Paint attentionPaint;

	public VerseItem(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		lText = V.get(this, R.id.lText);
		lVerseNumber = V.get(this, R.id.lVerseNumber);
		attributeView = V.get(this, R.id.attributeView);
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		if (collapsed) {
			setMeasuredDimension(getMeasuredWidth(), 0);
			return;
		}

		if ( Build.VERSION.SDK_INT >= 21) {
			// Fix bug on Lollipop where the last line of the text does not calculate line spacing mult/add.
			// https://code.google.com/p/android/issues/detail?id=77941

			final VerseTextView lText = this.lText;
			if (lText != null) {
				final Layout layout = lText.getLayout();

				if (layout != null) {
					final int lastLine = layout.getLineCount() - 1;
					final int spacing = lText.getIncludeFontPadding() ? (layout.getLineBottom(lastLine) - layout.getLineTop(lastLine)) : (layout.getLineDescent(lastLine) - layout.getLineAscent(lastLine));
					final int extra = (int) (spacing * (layout.getSpacingMultiplier() - 1) + layout.getSpacingAdd() + 0.5f);

					setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + extra);
				}
			}
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		final int w = getWidth();
		final int h = getHeight();

		if (checked) {
			Paint solid = checkedPaintSolid;
			if (solid == null) {
				checkedPaintSolid = solid = new Paint();
				final int colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default);
				final int color = ColorUtils.setAlphaComponent(colorRgb, 0xa0);
				solid.setColor(color);
				solid.setStyle(Paint.Style.FILL);
			}

			canvas.drawRect(0, 0, w, h, solid);
		}

		if (dragHover) {
			if (dragHoverBg == null) {
				//noinspection deprecation
				dragHoverBg = getResources().getDrawable(R.drawable.item_verse_bg_draghovered);
			}

			//noinspection ConstantConditions
			dragHoverBg.setBounds(0, 0, w, h);
			dragHoverBg.draw(canvas);
		}

		if (attentionStart != 0) {
			final long now = System.currentTimeMillis();
			final long elapsed = now - attentionStart;
			if (elapsed >= ATTENTION_DURATION) {
				attentionStart = 0;
			} else {
				final float opacity = 0.4f * (1.f - ((float) elapsed / ATTENTION_DURATION));
				Paint p = attentionPaint;
				if (p == null) {
					attentionPaint = p = new Paint();
					final int colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default);
					p.setColor(colorRgb);
					p.setStyle(Paint.Style.FILL);
				}

				p.setColor(ColorUtils.setAlphaComponent(p.getColor(), (int) (opacity * 255)));
				canvas.drawRect(0, 0, w, h, p);

				invalidate(); // animate
			}
		}

		super.onDraw(canvas);
	}

	void callAttention(final long attentionStartTime) {
		if (this.attentionStart != attentionStartTime) {
			this.attentionStart = attentionStartTime;

			if (attentionStartTime != 0) {
				setWillNotDraw(false);
			}
			invalidate();
		}
	}

	public static void invalidateSelectedVersePaints() {
		checkedPaintSolid = null;
		attentionPaint = null;
	}

	@Override
	public boolean isChecked() {
		return checked;
	}

	@Override
	public void setChecked(boolean checked) {
		if (this.checked != checked) {
			this.checked = checked;
			if (checked) {
				setWillNotDraw(false);
			}
			invalidate();
		}
	}

	public void setDragHover(final boolean dragHover) {
		if (this.dragHover != dragHover) {
			this.dragHover = dragHover;
			if (dragHover) {
				setWillNotDraw(false);
			}
			invalidate();
		}
	}

	@Override
	public void toggle() {
		setChecked(!checked);
	}

	public void setCollapsed(final boolean collapsed) {
		if (this.collapsed == collapsed) return;
		this.collapsed = collapsed;
		requestLayout();
	}

	public void setAri(final int ari) {
		this.ari = ari;
	}

	@Override
	public boolean onDragEvent(final DragEvent event) {
		// Dropping only works on verse not pericope or something else
		if (ari == 0) return false;

		switch (event.getAction()) {
			case DragEvent.ACTION_DRAG_STARTED:
				// Determines if this View can accept the dragged data
				if (event.getClipDescription().hasMimeType(PROGRESS_MARK_DRAG_MIME_TYPE)) {
					return true;
				}
				break;

			case DragEvent.ACTION_DRAG_ENTERED:
				// Indicate this will receive the drag data.
				setDragHover(true);
				invalidate();
				return true;

			case DragEvent.ACTION_DRAG_EXITED:
			case DragEvent.ACTION_DRAG_ENDED:
				// Indicate this will no more receive the drag data.
				setDragHover(false);
				invalidate();
				return true;

			case DragEvent.ACTION_DROP:
				App.trackEvent("pin_drop");
				final ClipData.Item item = event.getClipData().getItemAt(0);
				final int preset_id = Integer.parseInt(item.getText().toString());

				final ProgressMark progressMark = S.getDb().getProgressMarkByPresetId(preset_id);
				progressMark.ari = this.ari;
				progressMark.modifyTime = new Date();
				S.getDb().insertOrUpdateProgressMark(progressMark);

				App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));

				return true;
		}
		return false;
	}

	/**
	 * This is called AND used when explore-by-touch is off.
	 * Force TallkBack to use the content description and not explore the children of this view.
	 */
	@Override
	public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
		event.getText().add(getContentDescription());

		return true;
	}

	/**
	 * Make sure TalkBack reads the verse correctly by setting content description.
	 */
	@Override
	public CharSequence getContentDescription() {
		final StringBuilder res = new StringBuilder();

		if (lVerseNumber.length() > 0) {
			res.append(lVerseNumber.getText()).append(' ');
		}

		res.append(lText.getText());

		final int bookmark_count = attributeView.getBookmarkCount();
		if (bookmark_count == 1) {
			res.append(' ').append(getContext().getString(R.string.desc_verse_attribute_one_bookmark));
		} else if (bookmark_count > 1) {
			res.append(' ').append(getContext().getString(R.string.desc_verse_attribute_multiple_bookmarks, bookmark_count));
		}

		final int note_count = attributeView.getNoteCount();
		if (note_count == 1) {
			res.append(' ').append(getContext().getString(R.string.desc_verse_attribute_one_note));
		} else if (note_count > 1) {
			res.append(' ').append(getContext().getString(R.string.desc_verse_attribute_multiple_notes, note_count));
		}

		final int progress_mark_bits = attributeView.getProgressMarkBits();
		for (int preset_id = 0; preset_id < AttributeView.PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
			if ((progress_mark_bits & 1 << AttributeView.PROGRESS_MARK_BITS_START + preset_id) != 0) {
				final ProgressMark progressMark = S.getDb().getProgressMarkByPresetId(preset_id);

				final String caption;
				if (TextUtils.isEmpty(progressMark.caption)) {
					caption = getContext().getString(AttributeView.getDefaultProgressMarkStringResource(preset_id));
				} else {
					caption = progressMark.caption;
				}

				res.append(' ').append(getContext().getString(R.string.desc_verse_attribute_progress_mark, caption));
			}
		}

		return res;
	}
}
