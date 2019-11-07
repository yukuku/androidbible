package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

public class MultiColorView extends AppCompatTextView {
	float density;
	Paint p;
	int bgColor;
	int[] colors;
	
	public MultiColorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	private void init() {
		density = getResources().getDisplayMetrics().density;
		p = new Paint();
		
		if (isInEditMode()) {
			bgColor = 0xff000000;
			colors = new int[] {0xff0000ff, 0xff0ff0ff, 0xfff000ff, 0xff00ff00};
		}
	}
	
	public void setBgColor(int bgColor) {
		this.bgColor = bgColor;
		invalidate();
	}
	
	public void setColors(int[] colors) {
		this.colors = colors;
		invalidate();
	}

	@Override protected void onDraw(Canvas canvas) {
		// not calling super.onDraw(canvas)

		final int w = getWidth();
		final int h = getHeight();

		final float density = this.density;

		final float l = getTotalPaddingLeft() + density * 8;
		final float t = density * 8;
		final float r = w - getTotalPaddingRight() - density * 8;
		final float b = h - density * 8;

		// bg
		p.setColor(bgColor);
		canvas.drawRect(l, t, r, b, p);

		if (colors != null) {
			float l_inner = l + 2 * density;
			float t_inner = t + 2 * density;
			float r_inner = r - 2 * density;
			float b_inner = b - 2 * density;
			float box_w = (r_inner - l_inner - 2 * density * (colors.length - 1)) / colors.length;
			float box_off = (r_inner - l_inner + 2 * density) / colors.length;
			float l_current = l_inner;
			for (final int color : colors) {
				p.setColor(color);
				canvas.drawRect(l_current, t_inner, l_current + box_w, b_inner, p);
				l_current += box_off;
			}
		}
	}

}
