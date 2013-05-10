package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import yuku.alkitab.base.S;
import yuku.alkitab.base.U;


public class LabeledSplitHandleButton extends SplitHandleButton {
	public static final String TAG = LabeledSplitHandleButton.class.getSimpleName();

	float[] tmp_hsl = {0.f, 0.f, 0.f};
	Rect tmp_rect = new Rect();
	String label1 = null;
	String label2 = null;
	Paint labelPaint = new Paint();
	Paint bezelPaint = new Paint();
	float textSize = 11.5f;
	
	public LabeledSplitHandleButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	void init() {
		float density = getResources().getDisplayMetrics().density;
		labelPaint.setColor(0xffffffff);
		labelPaint.setShadowLayer(2.f * density, 0, 0, 0xff000000);
		labelPaint.setTextSize(textSize * density);
		labelPaint.setAntiAlias(true);
		bezelPaint.setStyle(Paint.Style.FILL_AND_STROKE);
	}
	
	public void setLabel1(String label1) {
		this.label1 = label1;
		invalidate();
	}
	
	public void setLabel2(String label2) {
		this.label2 = label2;
		invalidate();
	}

	@Override protected void onDraw(Canvas canvas) {
		// DO NOT CALL super.onDraw(canvas);
		
		float density = getResources().getDisplayMetrics().density;
		
		U.rgbToHsl(S.applied.backgroundColor, tmp_hsl);
		float orig_l = tmp_hsl[2];
		
		// background: make it same as action bar color
		int bgColor;
		if (isPressed()) {
			bgColor = 0xff247c94;
		} else {
			bgColor = 0xff222222;
		}
		canvas.drawColor(bgColor);
		
		// bezels: very dark and light version
		int bezelHeight = (int) (1.5f * density + 0.5);
		tmp_hsl[2] = orig_l * 0.6f;
		bezelPaint.setColor(U.hslToRgb(tmp_hsl) | 0xff000000);
		canvas.drawRect(0, 0, getWidth(), bezelHeight, bezelPaint);
		tmp_hsl[2] = (1.f + orig_l) * 0.5f;
		bezelPaint.setColor(U.hslToRgb(tmp_hsl) | 0xff000000);
		canvas.drawRect(0, getHeight() - bezelHeight, getWidth(), getHeight(), bezelPaint);
		
		if (label1 != null) {
			labelPaint.setTextAlign(Paint.Align.LEFT);
			canvas.drawText(label1, 8.f * density, getHeight() * 0.5f + textSize * density * 0.3f, labelPaint);
		}
		
		if (label2 != null) {
			labelPaint.setTextAlign(Paint.Align.RIGHT);
			canvas.drawText(label2, getWidth() - 8.f * density, getHeight() * 0.5f + textSize * density * 0.3f, labelPaint);
		}
	}
}
