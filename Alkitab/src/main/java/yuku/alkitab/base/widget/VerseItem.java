package yuku.alkitab.base.widget;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.ProgressMark;

import java.util.Date;

public class VerseItem extends LinearLayout implements Checkable {
	public static final String TAG = VerseItem.class.getSimpleName();
	public static final String PROGRESS_MARK_DRAG_MIME_TYPE = "application/vnd.yuku.alkitab.progress_mark.drag";

	private boolean checked;
	private Drawable checkedBg;
	private boolean collapsed;
	private boolean dragHover;
	private Drawable dragHoverBg;

	public VerseTextView lText;
	public TextView lVerseNumber;
	public AttributeView attributeView;

	/** the ari of the verse represented by this view. If this is 0, this is a pericope or something else. */
	private int ari;

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

		if (Build.VERSION.SDK_INT >= 21) {
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
		if (checked) {
			if (checkedBg == null) {
				checkedBg = getResources().getDrawable(R.drawable.item_verse_bg_checked);
			}

			checkedBg.setBounds(0, 0, getWidth(), getHeight());
			checkedBg.draw(canvas);
		}

		if (dragHover) {
			if (dragHoverBg == null) {
				dragHoverBg = getResources().getDrawable(R.drawable.item_verse_bg_draghovered);
			}

			dragHoverBg.setBounds(0, 0, getWidth(), getHeight());
			dragHoverBg.draw(canvas);
		}

		super.onDraw(canvas);
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
				final ClipData.Item item = event.getClipData().getItemAt(0);
				final int preset_id = Integer.parseInt(item.getText().toString());

				final ProgressMark progressMark = S.getDb().getProgressMarkByPresetId(preset_id);
				progressMark.ari = this.ari;
				progressMark.modifyTime = new Date();
				S.getDb().updateProgressMark(progressMark);

				final Intent intent = new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED);
				intent.putExtra(IsiActivity.EXTRA_CLOSE_DRAWER, true);
				App.getLbm().sendBroadcast(intent);

				return true;
		}
		return false;
	}
}
