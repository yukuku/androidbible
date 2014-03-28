package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;


public class LabeledSplitHandleButton extends SplitHandleButton {
	public static final String TAG = LabeledSplitHandleButton.class.getSimpleName();

	float[] tmp_hsl = {0.f, 0.f, 0.f};
	String label1 = null;
	String label2 = null;
	Paint labelPaint = new Paint();
	Paint bezelPaint = new Paint();
	float textSize = 11.5f;
	float label1width = 0;
	float label2width = 0;
	boolean label1pressed = false;
	boolean label2pressed = false;
	boolean label1down = false;
	boolean label2down = false;
	float density;

	OnLabelPressed onLabelPressed;

	public interface OnLabelPressed {
		void onLabelPressed(int which);
	}

	public LabeledSplitHandleButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	void init() {
		density = getResources().getDisplayMetrics().density;
		labelPaint.setColor(0xffffffff);
		labelPaint.setShadowLayer(2.f * density, 0, 0, 0xff000000);
		labelPaint.setTextSize(textSize * density);
		labelPaint.setAntiAlias(true);
		bezelPaint.setStyle(Paint.Style.FILL_AND_STROKE);
	}

	public void setOnLabelPressed(final OnLabelPressed onLabelPressed) {
		this.onLabelPressed = onLabelPressed;
	}

	public void setLabel1(String label1) {
		this.label1 = label1;
		invalidate();
	}
	
	public void setLabel2(String label2) {
		this.label2 = label2;
		invalidate();
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		// check if touch is at label1, label2 or neither
		int maxLabel1w = (int) Math.min(140 * density, label1width);
		int maxLabel2w = (int) Math.min(140 * density, label2width);

		final int action = MotionEventCompat.getActionMasked(event);
		if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			float x = event.getX();

			if (action == MotionEvent.ACTION_DOWN) {
				label1down = x < maxLabel1w;
				label2down = x > getWidth() - maxLabel2w;
			}

			if (action == MotionEvent.ACTION_UP && onLabelPressed != null) {
				label1pressed = label1down && x < maxLabel1w;
				label2pressed = label2down && x > getWidth() - maxLabel2w;

				if (label1pressed) {
					post(new Runnable() {
						@Override
						public void run() {
							onLabelPressed.onLabelPressed(1);
							label1down = false;
							postInvalidate();
						}
					});
				} else if (label2pressed) {
					post(new Runnable() {
						@Override
						public void run() {
							onLabelPressed.onLabelPressed(2);
							label2down = false;
							postInvalidate();
						}
					});
				}
			}
		}

		boolean res = super.onTouchEvent(event);
		postInvalidate();
		return res;
	}

	@Override protected void onDraw(Canvas canvas) {
		// DO NOT CALL super.onDraw(canvas);

		U.rgbToHsl(S.applied.backgroundColor, tmp_hsl);
		float orig_l = tmp_hsl[2];
		
		// always draw unpressed bg color first
		canvas.drawColor(0xff222222);

		if (label1down || label2down) {
			canvas.save();
			if (label1down) {
				canvas.clipRect(0, 0, label1width, getHeight());
			} else if (label2down) {
				canvas.clipRect(getWidth() - label2width, 0, getWidth(), getHeight());
			}
			canvas.drawColor(0xff247c94);
			canvas.restore();
		} else {
			if (isPressed()) { // not label1 nor label2
				canvas.drawColor(0xff247c94);
			}
		}

		// bezels: very dark and light version
		int bezelHeight = (int) (1.5f * density + 0.5);
		tmp_hsl[2] = (1.f + orig_l) * 0.5f;
		bezelPaint.setColor(U.hslToRgb(tmp_hsl) | 0xff000000);
		canvas.drawRect(0, getHeight() - bezelHeight, getWidth(), getHeight(), bezelPaint);
		
		if (label1 != null) {
			labelPaint.setTextAlign(Paint.Align.LEFT);
			label1width = 16.f * density + labelPaint.measureText(label1);
			canvas.drawText(label1, 8.f * density, getHeight() * 0.5f + textSize * density * 0.3f, labelPaint);
		}
		
		if (label2 != null) {
			labelPaint.setTextAlign(Paint.Align.RIGHT);
			label2width = 16.f * density + labelPaint.measureText(label2);
			canvas.drawText(label2, getWidth() - 8.f * density, getHeight() * 0.5f + textSize * density * 0.3f, labelPaint);
		}
	}
}
