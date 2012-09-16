package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;

import yuku.alkitab.R;

public class VerseItem extends LinearLayout implements Checkable {
	public static final String TAG = VerseItem.class.getSimpleName();
	
	private boolean checked;
	private Drawable checkedBg;
	
	public VerseItem(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override protected void onDraw(Canvas canvas) {
		if (checked) {
			if (checkedBg == null) {
				checkedBg = getResources().getDrawable(R.drawable.item_verse_bg_checked);
			}
			
			checkedBg.setBounds(0, 0, getWidth(), getHeight());
			checkedBg.draw(canvas);
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
}
