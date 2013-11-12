package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;
import yuku.alkitab.debug.R;

public class VerseItem extends LinearLayout implements Checkable {
	public static final String TAG = VerseItem.class.getSimpleName();

	private boolean shaded;
	private Drawable shadedBg;
	private boolean checked;
	private Drawable checkedBg;
	private boolean collapsed;

	public VerseItem(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (collapsed) {
			setMeasuredDimension(getMeasuredWidth(), 0);
		}
	}

	@Override protected void onDraw(Canvas canvas) {
		if (checked) {
			if (checkedBg == null) {
				checkedBg = getResources().getDrawable(R.drawable.item_verse_bg_checked);
			}

			checkedBg.setBounds(0, 0, getWidth(), getHeight());
			checkedBg.draw(canvas);
		}

		if (shaded) {
			if (shadedBg == null) {
				shadedBg = getResources().getDrawable(R.drawable.reading_plan_disabled_verses_shade_tiled);
			}

			shadedBg.setBounds(0, 0, getWidth(), getHeight());
			shadedBg.draw(canvas);
		}

		super.onDraw(canvas);
	}
	
	@Override public boolean isChecked() {
		return checked;
	}

	@Override public void setChecked(boolean checked) {
		if (this.checked != checked) {
			this.checked = checked;
			if (checked) {
				setWillNotDraw(false);
			}
			invalidate();
		}
	}

	@Override public void toggle() {
		setChecked(!checked);
	}

	public void setShaded(boolean shaded) {
		this.shaded = shaded;
		if (shaded) {
			setWillNotDraw(false);
		}
		invalidate();
	}

	public void setCollapsed(final boolean collapsed) {
		if (this.collapsed == collapsed) return;
		this.collapsed = collapsed;
		requestLayout();
	}
}
