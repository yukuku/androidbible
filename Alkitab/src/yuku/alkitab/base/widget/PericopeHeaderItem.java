package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import yuku.alkitab.debug.R;

public class PericopeHeaderItem extends LinearLayout {
	public static final String TAG = PericopeHeaderItem.class.getSimpleName();

	private boolean shaded;
	private Drawable shadedBg;

	public PericopeHeaderItem(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override protected void onDraw(Canvas canvas) {
		if (shaded) {
			if (shadedBg == null) {
				shadedBg = getResources().getDrawable(R.drawable.reading_plan_disabled_verses_shade_tiled);
			}

			shadedBg.setBounds(0, 0, getWidth(), getHeight());
			shadedBg.draw(canvas);
		}

		super.onDraw(canvas);
	}

	public void setShaded(boolean shaded) {
		this.shaded = shaded;
		if (shaded) {
			setWillNotDraw(false);
		}
		invalidate();
	}
}
