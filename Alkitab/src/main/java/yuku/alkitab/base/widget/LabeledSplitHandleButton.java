package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import yuku.alkitab.debug.R;


public class LabeledSplitHandleButton extends SplitHandleButton {
	public static final String TAG = LabeledSplitHandleButton.class.getSimpleName();

	String label1 = null;
	String label2 = null;
	Paint labelPaint = new Paint();
	Paint bezelPaint = new Paint();
	float textSize = 14f;
	float label1width;
	float label2width;
	float rotatewidth;
	boolean label1pressed = false;
	boolean label2pressed = false;
	boolean rotatepressed = false;
	boolean label1down = false;
	boolean label2down = false;
	boolean rotatedown = false;
	float density;

	ButtonPressListener buttonPressListener;
	int primaryColor;
	int accentColor;

	public enum Button {
		start,
		end,
		rotate,
	}

	public interface ButtonPressListener {
		void onLabelPressed(Button button);
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
		labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
		labelPaint.setAntiAlias(true);
		bezelPaint.setStyle(Paint.Style.FILL_AND_STROKE);

		primaryColor = getResources().getColor(R.color.primary);
		accentColor = getResources().getColor(R.color.accent);

		rotatewidth = getResources().getDimensionPixelSize(R.dimen.split_handle_thickness);
	}

	public void setButtonPressListener(final ButtonPressListener buttonPressListener) {
		this.buttonPressListener = buttonPressListener;
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
		// check if touch is at label1, label2, rotate button, or neither
		int maxLabel1w = (int) Math.min(140 * density, label1width);
		int maxLabel2w = (int) Math.min(140 * density, label2width);

		final int action = MotionEventCompat.getActionMasked(event);
		if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			float x = event.getX();

			if (action == MotionEvent.ACTION_DOWN) {
				label1down = x < maxLabel1w;
				label2down = x > getWidth() - maxLabel2w;
				rotatedown = x >= (getWidth() - rotatewidth) * 0.5f && x <= (getWidth() + rotatewidth) * 0.5f;
			}

			if (action == MotionEvent.ACTION_UP && buttonPressListener != null) {
				label1pressed = label1down && x < maxLabel1w;
				label2pressed = label2down && x > getWidth() - maxLabel2w;
				rotatepressed = rotatedown && x >= (getWidth() - rotatewidth) * 0.5f && x <= (getWidth() + rotatewidth) * 0.5f;

				if (rotatepressed) {
					orientation = orientation == Orientation.horizontal ? Orientation.vertical : Orientation.horizontal;
					post(() -> buttonPressListener.onLabelPressed(Button.rotate));
				} else if (label1pressed) {
					post(() -> buttonPressListener.onLabelPressed(Button.start));
				} else if (label2pressed) {
					post(() -> buttonPressListener.onLabelPressed(Button.end));
				}

				if (rotatepressed || label1pressed || label2pressed) {
					post(() -> {
						rotatedown = label1down = label2down = false;
						postInvalidate();
					});
				}
			}

			if ((action == MotionEvent.ACTION_UP && !label1pressed && !label2pressed && !rotatepressed) || action == MotionEvent.ACTION_CANCEL) {
				label1down = label1pressed = false;
				label2down = label2pressed = false;
				rotatedown = rotatepressed = false;
			}
		}

		final boolean res = super.onTouchEvent(event);
		postInvalidate();
		return res;
	}

	@Override protected void onDraw(@NonNull Canvas canvas) {
		// DO NOT CALL super.onDraw(canvas);

		// always draw unpressed bg color first
		canvas.drawColor(primaryColor);

		if (label1down || label2down || rotatedown) {
			canvas.save();
			if (rotatedown) {
				canvas.clipRect((getWidth() - rotatewidth) * 0.5f, 0, (getWidth() + rotatewidth) * 0.5f, getHeight());
			} else if (label1down) {
				canvas.clipRect(0, 0, label1width, getHeight());
			} else if (label2down) {
				canvas.clipRect(getWidth() - label2width, 0, getWidth(), getHeight());
			}
			canvas.drawColor(accentColor);
			canvas.restore();
		} else {
			if (isPressed()) { // not label1 nor label2
				canvas.drawColor(accentColor);
			}
		}

		bezelPaint.setColor(0xff111111);
		final int bezelHeight = (int) (1.5f * density + 0.5);
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
